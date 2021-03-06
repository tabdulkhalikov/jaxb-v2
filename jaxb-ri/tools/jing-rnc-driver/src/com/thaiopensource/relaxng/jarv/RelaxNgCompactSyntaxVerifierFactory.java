/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2017 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://oss.oracle.com/licenses/CDDL+GPL-1.1
 * or LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
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

package com.thaiopensource.relaxng.jarv;

import java.io.IOException;

import org.iso_relax.verifier.Schema;
import org.iso_relax.verifier.VerifierConfigurationException;
import org.iso_relax.verifier.VerifierFactory;
import com.sun.tools.rngdatatype.DatatypeLibraryFactory;
import com.sun.tools.rngdatatype.helpers.DatatypeLibraryLoader;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.thaiopensource.relaxng.impl.SchemaBuilderImpl;
import com.thaiopensource.relaxng.impl.SchemaPatternBuilder;
import com.thaiopensource.relaxng.parse.Parseable;
import com.thaiopensource.relaxng.parse.compact.CompactParseable;
import com.thaiopensource.validate.IncorrectSchemaException;
import com.thaiopensource.xml.sax.DraconianErrorHandler;

/**
 * {@link org.iso_relax.verifier.VerifierFactory} implementation
 * for RELAX NG Compact Syntax.
 * 
 * <p>
 * The reason why this class is in this package is to access
 * some of the package-private classes.
 * 
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public class RelaxNgCompactSyntaxVerifierFactory extends VerifierFactory {
    private final DatatypeLibraryFactory dlf = new DatatypeLibraryLoader();
    private final ErrorHandler eh = new DraconianErrorHandler();

    public Schema compileSchema(InputSource is) throws VerifierConfigurationException, SAXException, IOException {
        SchemaPatternBuilder spb = new SchemaPatternBuilder();
        Parseable parseable = new CompactParseable(is, eh);
        try {
          return new SchemaImpl(SchemaBuilderImpl.parse(parseable, eh, dlf, spb, false), spb);
        }
        catch (IncorrectSchemaException e) {
          throw new SAXException("unreported schema error");
        }
    }

}
