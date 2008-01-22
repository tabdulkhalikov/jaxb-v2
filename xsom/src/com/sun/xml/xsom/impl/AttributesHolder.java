/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 * 
 * Contributor(s):
 * 
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the "License").  You may not use this file except
 * in compliance with the License.
 * 
 * You can obtain a copy of the license at
 * https://jwsdp.dev.java.net/CDDLv1.0.html
 * See the License for the specific language governing
 * permissions and limitations under the License.
 * 
 * When distributing Covered Code, include this CDDL
 * HEADER in each file and include the License file at
 * https://jwsdp.dev.java.net/CDDLv1.0.html  If applicable,
 * add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your
 * own identifying information: Portions Copyright [yyyy]
 * [name of copyright owner]
 */
package com.sun.xml.xsom.impl;

import com.sun.xml.xsom.XSAttGroupDecl;
import com.sun.xml.xsom.XSAttributeUse;
import com.sun.xml.xsom.impl.parser.SchemaDocumentImpl;
import com.sun.xml.xsom.impl.scd.Iterators;
import com.sun.xml.xsom.impl.Ref.AttGroup;
import org.xml.sax.Locator;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.LinkedHashMap;

public abstract class AttributesHolder extends DeclarationImpl {

    protected AttributesHolder( SchemaDocumentImpl _parent, AnnotationImpl _annon,
                                Locator loc, ForeignAttributesImpl _fa, String _name, boolean _anonymous ) {

        super(_parent,_annon,loc,_fa,_parent.getTargetNamespace(),_name,_anonymous);
    }

    /** set the local wildcard. */
    public abstract void setWildcard(WildcardImpl wc);

    /**
     * Local attribute use.
     * Use linked hash map to guarantee the iteration order, and make it close to
     * what was in the schema document.
     */
    protected final Map<UName,AttributeUseImpl> attributes = new LinkedHashMap<UName,AttributeUseImpl>();
    public void addAttributeUse( UName name, AttributeUseImpl a ) {
        attributes.put( name, a );
    }
    /** prohibited attributes. */
    protected final Set<UName> prohibitedAtts = new HashSet<UName>();
    public void addProhibitedAttribute( UName name ) {
        prohibitedAtts.add(name);
    }

    /**
     * Returns the attribute uses by looking at attribute groups and etc.
     * Searching for the base type is done in {@link ComplexTypeImpl}.
     */
    public Collection<XSAttributeUse> getAttributeUses() {
        // TODO: this is fairly inefficient
        List<XSAttributeUse> v = new ArrayList<XSAttributeUse>();
        v.addAll(attributes.values());
        for( XSAttGroupDecl agd : getAttGroups() )
            v.addAll(agd.getAttributeUses());
        return v;
    }
    public Iterator<XSAttributeUse> iterateAttributeUses() {
        return getAttributeUses().iterator();
    }



    public XSAttributeUse getDeclaredAttributeUse( String nsURI, String localName ) {
        return attributes.get(new UName(nsURI,localName));
    }

    public Iterator<AttributeUseImpl> iterateDeclaredAttributeUses() {
        return attributes.values().iterator();
    }

    public Collection<AttributeUseImpl> getDeclaredAttributeUses() {
        return attributes.values();
    }


    /** {@link Ref.AttGroup}s that are directly refered from this. */
    protected final Set<Ref.AttGroup> attGroups = new HashSet<Ref.AttGroup>();

    public void addAttGroup( Ref.AttGroup a ) { attGroups.add(a); }

    // Iterates all AttGroups which are directly referenced from this component
    // this does not iterate att groups referenced from the base type
    public Iterator<XSAttGroupDecl> iterateAttGroups() {
        return new Iterators.Adapter<XSAttGroupDecl,Ref.AttGroup>(attGroups.iterator()) {
            protected XSAttGroupDecl filter(AttGroup u) {
                return u.get();
            }
        };
    }

    public Set<XSAttGroupDecl> getAttGroups() {
        return new AbstractSet<XSAttGroupDecl>() {
            public Iterator<XSAttGroupDecl> iterator() {
                return iterateAttGroups();
            }

            public int size() {
                return attGroups.size();
            }
        };
    }
}
