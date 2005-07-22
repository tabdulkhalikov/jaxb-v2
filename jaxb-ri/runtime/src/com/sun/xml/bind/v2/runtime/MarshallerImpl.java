/*
 * Copyright 2003 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package com.sun.xml.bind.v2.runtime;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;

import javax.xml.bind.DatatypeConverter;
import javax.xml.bind.JAXBException;
import javax.xml.bind.MarshalException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.PropertyException;
import javax.xml.bind.ValidationEvent;
import javax.xml.bind.ValidationEventHandler;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.attachment.AttachmentMarshaller;
import javax.xml.bind.helpers.AbstractMarshallerImpl;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Result;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamResult;
import javax.xml.validation.Schema;
import javax.xml.validation.ValidatorHandler;

import com.sun.xml.bind.DatatypeConverterImpl;
import com.sun.xml.bind.marshaller.CharacterEscapeHandler;
import com.sun.xml.bind.marshaller.DataWriter;
import com.sun.xml.bind.marshaller.DumbEscapeHandler;
import com.sun.xml.bind.marshaller.Messages;
import com.sun.xml.bind.marshaller.MinimumEscapeHandler;
import com.sun.xml.bind.marshaller.NamespacePrefixMapper;
import com.sun.xml.bind.marshaller.NioEscapeHandler;
import com.sun.xml.bind.marshaller.SAX2DOMEx;
import com.sun.xml.bind.marshaller.XMLWriter;
import com.sun.xml.bind.v2.AssociationMap;
import com.sun.xml.bind.v2.FatalAdapter;
import com.sun.xml.bind.v2.runtime.output.C14nXmlOutput;
import com.sun.xml.bind.v2.runtime.output.Encoded;
import com.sun.xml.bind.v2.runtime.output.ForkXmlOutput;
import com.sun.xml.bind.v2.runtime.output.IndentingUTF8XmlOutput;
import com.sun.xml.bind.v2.runtime.output.SAXOutput;
import com.sun.xml.bind.v2.runtime.output.UTF8XmlOutput;
import com.sun.xml.bind.v2.runtime.output.XMLEventWriterOutput;
import com.sun.xml.bind.v2.runtime.output.XMLStreamWriterOutput;
import com.sun.xml.bind.v2.runtime.output.XmlOutput;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.XMLFilterImpl;

/**
 * Implementation of {@link Marshaller} interface for the JAXB RI.
 *
 * <p>
 * Eventually all the {@link #marshal} methods call into
 * the {@link #write} method.
 * 
 * @author Kohsuke Kawaguchi
 * @author Vivek Pandey
 */
public /*to make unit tests happy*/ final class MarshallerImpl extends AbstractMarshallerImpl implements ValidationEventHandler
{
    /** Indentation string. Default is four whitespaces. */
    private String indent = "    ";
    
    /** Used to assign prefixes to namespace URIs. */
    private NamespacePrefixMapper prefixMapper = null;
    
    /** Object that handles character escaping. */
    private CharacterEscapeHandler escapeHandler = null; 
    
    /** Whether the xml declaration will be printed or not. */
    private boolean printXmlDeclaration = true;
    
    /** XML BLOB written after the XML declaration. */
    private String header=null;

    /** reference to the context that created this object */
    final JAXBContextImpl context;

    protected final XMLSerializer serializer;

    /**
     * Non-null if we work inside a {@link BinderImpl}.
     */
    private final AssociationMap assoc;

    /**
     * Non-null if we do the marshal-time validation.
     */
    private Schema schema;

    /**
     * @param assoc
     *      non-null if the marshaller is working inside {@link BinderImpl}.
     */
    public MarshallerImpl( JAXBContextImpl c, AssociationMap assoc ) {
        // initialize datatype converter with ours
        DatatypeConverter.setDatatypeConverter(DatatypeConverterImpl.theInstance);
        
        context = c;
        this.assoc = assoc;
        serializer = new XMLSerializer(this);

        try {
            setEventHandler(this);
        } catch (JAXBException e) {
            throw new AssertionError(e);    // impossible
        }
    }

    public void marshal(Object obj, XMLStreamWriter writer) throws JAXBException {
        write(obj, new XMLStreamWriterOutput(writer), new StAXPostInitAction(writer,serializer));
    }

    public void marshal(Object obj, XMLEventWriter writer) throws JAXBException {
        write(obj, new XMLEventWriterOutput(writer), new StAXPostInitAction(writer,serializer));
    }

    public void marshal(Object obj, XmlOutput output) throws JAXBException {
        write(obj, output, null );
    }

    public void marshal(Object target,Result result) throws JAXBException {
        if (result instanceof SAXResult) {
            write(target, new SAXOutput(((SAXResult) result).getHandler()), null);
            return;
        }
        if (result instanceof DOMResult) {
            final Node node = ((DOMResult) result).getNode();

            if (node == null) {
                Document doc = JAXBContextImpl.createDom();
                ((DOMResult) result).setNode(doc);
                write(target, new SAXOutput(new SAX2DOMEx(doc)), null );
            } else {
                write(target, new SAXOutput(new SAX2DOMEx(node)), new DomPostInitAction(node,serializer));
            }

            return;
        }
        if (result instanceof StreamResult) {
            StreamResult sr = (StreamResult) result;
            XmlOutput w = null;

            if (sr.getWriter() != null)
                w = createWriter(sr.getWriter());
            else if (sr.getOutputStream() != null)
                w = createWriter(sr.getOutputStream());
            else if (sr.getSystemId() != null) {
                String fileURL = sr.getSystemId();

                if (fileURL.startsWith("file:///")) {
                    if (fileURL.substring(8).indexOf(":") > 0)
                        fileURL = fileURL.substring(8);
                    else
                        fileURL = fileURL.substring(7);
                } // otherwise assume that it's a file name

                try {
                    w = createWriter(new FileOutputStream(fileURL));
                } catch (IOException e) {
                    throw new MarshalException(e);
                }
            }

            if (w == null)
                throw new IllegalArgumentException();

            write(target, w, null);
            return;
        }

        // unsupported parameter type
        throw new MarshalException( 
            Messages.format( Messages.UNSUPPORTED_RESULT ) );
    }

    /**
     * Used by {@link BridgeImpl} to write an arbitrary object.
     */
    protected final <T> void write(Name rootTagName, JaxBeanInfo<T> bi, T obj, XmlOutput out,Runnable postInitAction) throws JAXBException {
        try {
            prewrite(out, true, postInitAction);
            serializer.startElement(rootTagName,null);
            if(bi.jaxbType==Void.class || bi.jaxbType==void.class) {
                // special case for void
                serializer.endNamespaceDecls(null);
                serializer.endAttributes();
            } else { // normal cases
                if(obj==null)
                    serializer.writeXsiNilTrue();
                else
                    serializer.childAsXsiType(obj,"root",bi);
            }
            serializer.endElement();
            postwrite(out);
        } catch( SAXException e ) {
            throw new MarshalException(e);
        } catch (IOException e) {
            throw new MarshalException(e);
        } catch (XMLStreamException e) {
            throw new MarshalException(e);
        } finally {
            serializer.close();
        }
    }

    /**
     * All the marshal method invocation eventually comes down to this call.
     */
    private void write(Object obj, XmlOutput out, Runnable postInitAction)
        throws JAXBException {

        if( obj == null )
            throw new IllegalArgumentException(Messages.format(Messages.NOT_MARSHALLABLE));

        if( schema!=null ) {
            // send the output to the validator as well
            ValidatorHandler validator = schema.newValidatorHandler();
            validator.setErrorHandler(new FatalAdapter(serializer));
            // work around a bug in JAXP validator in Tiger
            XMLFilterImpl f = new XMLFilterImpl() {
                public void startPrefixMapping(String prefix, String uri) throws SAXException {
                    super.startPrefixMapping(prefix.intern(), uri.intern());
                }
            };
            f.setContentHandler(validator);
            out = new ForkXmlOutput( new SAXOutput(f), out );
        }

        try {
            prewrite(out,isFragment(),postInitAction);
            serializer.childAsRoot(obj);
            postwrite(out);
        } catch( SAXException e ) {
            throw new MarshalException(e);
        } catch (IOException e) {
            throw new MarshalException(e);
        } catch (XMLStreamException e) {
            throw new MarshalException(e);
        } finally {
            serializer.close();
        }
    }

    // common parts between two write methods.

    private void prewrite(XmlOutput out, boolean fragment, Runnable postInitAction) throws IOException, SAXException, XMLStreamException {
        serializer.startDocument(out,fragment,getSchemaLocation(),getNoNSSchemaLocation());
        if(postInitAction!=null)    postInitAction.run();
        if(prefixMapper!=null) {
            // be defensive as we work with the user's code
            String[] decls = prefixMapper.getContextualNamespaceDecls();
            if(decls!=null) { // defensive check
                for( int i=0; i<decls.length; i+=2 ) {
                    String prefix = decls[i];
                    String nsUri = decls[i+1];
                    if(nsUri!=null && prefix!=null) // defensive check
                        serializer.addInscopeBinding(nsUri,prefix);
                }
            }
        }
        serializer.setPrefixMapper(prefixMapper);
    }

    private void postwrite(XmlOutput out) throws IOException, SAXException, XMLStreamException {
        serializer.endDocument();
        serializer.reconcileID();   // extra check
        out.flush();
    }


    //
    //
    // create XMLWriter by specifing various type of output.
    //
    //
    
    protected CharacterEscapeHandler createEscapeHandler( String encoding ) {
        if( escapeHandler!=null )
            // user-specified one takes precedence.
            return escapeHandler;
        
        if( encoding.startsWith("UTF") )
            // no need for character reference. Use the handler
            // optimized for that pattern.
            return MinimumEscapeHandler.theInstance;
        
        // otherwise try to find one from the encoding
        try {
            // try new JDK1.4 NIO
            return new NioEscapeHandler( getJavaEncoding(encoding) );
        } catch( Throwable e ) {
            // if that fails, fall back to the dumb mode
            return DumbEscapeHandler.theInstance;
        }
    }
            
    public XmlOutput createWriter( Writer w, String encoding ) {
        
        // buffering improves the performance
        w = new BufferedWriter(w);
        
        CharacterEscapeHandler ceh = createEscapeHandler(encoding);
        XMLWriter xw;
        
        if(isFormattedOutput()) {
            DataWriter d = new DataWriter(w,encoding,ceh);
            d.setIndentStep(indent);
            xw=d;
        } 
        else
            xw = new XMLWriter(w,encoding,ceh);

        // Marshaller.JAXB_FRAGMENT property overrides our c.s.x.b.xmlDeclaration vendor property
        xw.setXmlDecl(printXmlDeclaration && !isFragment());
        xw.setHeader(header);
        return new SAXOutput(xw);   // TODO: don't we need a better writer?
    }

    public XmlOutput createWriter(Writer w) {
        return createWriter(w, getEncoding());
    }
    
    public XmlOutput createWriter( OutputStream os ) throws JAXBException {
        return createWriter(os, getEncoding());
    }
    
    public XmlOutput createWriter( OutputStream os, String encoding ) throws JAXBException {
        // buffering improves the performance, but not always
        if(!(os instanceof BufferedOutputStream) && !(os instanceof ByteArrayOutputStream))
            os = new BufferedOutputStream(os);

        if(encoding.equals("UTF-8")) {
            Encoded[] table = context.getUTF8NameTable();
            if(isFormattedOutput())
                return new IndentingUTF8XmlOutput(os,indent,table);
            else {
                if(context.c14nSupport)
                    return new C14nXmlOutput(os,table);
                else
                    return new UTF8XmlOutput(os,table);
            }
        }

        try {
            return createWriter(
                new OutputStreamWriter(os,getJavaEncoding(encoding)),
                encoding );
        } catch( UnsupportedEncodingException e ) {
            throw new MarshalException(
                Messages.format( Messages.UNSUPPORTED_ENCODING, encoding ),
                e );
        }
    }
    
    
    public Object getProperty(String name) throws PropertyException {
        if( INDENT_STRING.equals(name) )
            return indent;
        if( ENCODING_HANDLER.equals(name) )
            return escapeHandler;
        if( PREFIX_MAPPER.equals(name) )
            return prefixMapper;
        if( XMLDECLARATION.equals(name) )
            return printXmlDeclaration;
        if( XML_HEADERS.equals(name) )
            return header;

        return super.getProperty(name);
    }

    public void setProperty(String name, Object value) throws PropertyException {
        if( INDENT_STRING.equals(name) ) {
            indent = (String)value;
            return;
        }
        if( ENCODING_HANDLER.equals(name) ) {
            escapeHandler = (CharacterEscapeHandler)value;
            return;
        }
        if( PREFIX_MAPPER.equals(name) ) {
            prefixMapper = (NamespacePrefixMapper)value;
            return;
        }
        if( XMLDECLARATION.equals(name) ) {
            printXmlDeclaration = (Boolean) value;
            return;
        }
        if( XML_HEADERS.equals(name) ) {
            header = (String)value;
            return;
        }

        super.setProperty(name, value);
    }


    @Override
    public <A extends XmlAdapter> void setAdapter(Class<A> type, A adapter) {
        if(type==null)
            throw new IllegalArgumentException();
        serializer.putAdapter(type,adapter);
    }

    @Override
    public <A extends XmlAdapter> A getAdapter(Class<A> type) {
        if(type==null)
            throw new IllegalArgumentException();
        if(serializer.containsAdapter(type))
            // so as not to create a new instance when this method is called
            return serializer.getAdapter(type);
        else
            return null;
    }

    @Override
    public void setAttachmentMarshaller(AttachmentMarshaller am) {
        serializer.attachmentMarshaller = am;
    }

    @Override
    public AttachmentMarshaller getAttachmentMarshaller() {
        return serializer.attachmentMarshaller;
    }

    public Schema getSchema() {
        return schema;
    }

    public void setSchema(Schema s) {
        this.schema = s;
    }

    /**
     * Default error handling behavior fot {@link Marshaller}.
     */
    public boolean handleEvent(ValidationEvent event) {
        // draconian by default
        return false;
    }


    private static final String INDENT_STRING = "com.sun.xml.bind.indentString";
    private static final String PREFIX_MAPPER = "com.sun.xml.bind.namespacePrefixMapper"; 
    private static final String ENCODING_HANDLER = "com.sun.xml.bind.characterEscapeHandler"; 
    private static final String XMLDECLARATION = "com.sun.xml.bind.xmlDeclaration"; 
    private static final String XML_HEADERS = "com.sun.xml.bind.xmlHeaders";
}
