/*******************************************************************************
 * Copyright (c) 2010, 2015 Oracle.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution. 
 * The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 * and the Apache License v2.0 is available at 
 *     http://www.opensource.org/licenses/apache2.0.php.
 * You may elect to redistribute this code under either of these licenses.
 *
 * Contributors:
 *     Bob Nettleton (Oracle) - Initial Reference Implementation
 ******************************************************************************/  

package org.eclipse.gemini.naming;

import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NameParser;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.OperationNotSupportedException;

/**
 * This class implements an adapter for use in creating JNDI Context
 * implementations.  
 * 
 * This implementation throws the OperationNotSupportedException in each
 * method implementation for Context.  This allows subclasses to only override
 * the behavior that should be supported.  
 *
 * @version $Revision$
 */
class NotSupportedContext implements Context {

	private final String m_exceptionMessage;
	
	public NotSupportedContext(String exceptionMessage) {
		m_exceptionMessage = exceptionMessage;
	}
	
	@Override
	public Object addToEnvironment(String var0, Object var1) throws NamingException {
		operationNotSupported();
		return null;
	}

	@Override
	public void bind(String var0, Object var1) throws NamingException {
		operationNotSupported();
	}

	@Override
	public void bind(Name var0, Object var1) throws NamingException {
		operationNotSupported();
	}

	@Override
	public void close() throws NamingException {
		operationNotSupported();
	}

	@Override
	public String composeName(String var0, String var1) throws NamingException {
		operationNotSupported();
		return null;
	}

	@Override
	public Name composeName(Name var0, Name var1) throws NamingException {
		operationNotSupported();
		return null;
	}

	@Override
	public Context createSubcontext(String var0) throws NamingException {
		operationNotSupported();
		return null;
	}

	@Override
	public Context createSubcontext(Name var0) throws NamingException {
		operationNotSupported();
		return null;
	}

	@Override
	public void destroySubcontext(String var0) throws NamingException {
		operationNotSupported();
	}

	@Override
	public void destroySubcontext(Name var0) throws NamingException {
		operationNotSupported();
	}

	@Override
	public Hashtable getEnvironment() throws NamingException {
		operationNotSupported();
		return null;
	}

	@Override
	public String getNameInNamespace() throws NamingException {
		operationNotSupported();
		return null;
	}

	@Override
	public NameParser getNameParser(String var0) throws NamingException {
		operationNotSupported();
		return null;
	}

	@Override
	public NameParser getNameParser(Name var0) throws NamingException {
		operationNotSupported();
		return null;
	}

	@Override
	public NamingEnumeration list(String var0) throws NamingException {
		operationNotSupported();
		return null;
	}

	@Override
	public NamingEnumeration list(Name var0) throws NamingException {
		operationNotSupported();
		return null;
	}

	@Override
	public NamingEnumeration listBindings(String var0) throws NamingException {
		operationNotSupported();
		return null;
	}

	@Override
	public NamingEnumeration listBindings(Name var0) throws NamingException {
		operationNotSupported();
		return null;
	}

	@Override
	public Object lookup(String var0) throws NamingException {
		operationNotSupported();
		return null;
	}

	@Override
	public Object lookup(Name var0) throws NamingException {
		operationNotSupported();
		return null;
	}

	@Override
	public Object lookupLink(String var0) throws NamingException {
		operationNotSupported();
		return null;
	}

	@Override
	public Object lookupLink(Name var0) throws NamingException {
		operationNotSupported();
		return null;
	}

	@Override
	public void rebind(String var0, Object var1) throws NamingException {
		operationNotSupported();
	}

	@Override
	public void rebind(Name var0, Object var1) throws NamingException {
		operationNotSupported();
	}

	@Override
	public Object removeFromEnvironment(String var0) throws NamingException {
		operationNotSupported();
		return null;
	}

	@Override
	public void rename(String var0, String var1) throws NamingException {
		operationNotSupported();
	}

	@Override
	public void rename(Name var0, Name var1) throws NamingException {
		operationNotSupported();
	}

	@Override
	public void unbind(String var0) throws NamingException {
		operationNotSupported();
	}

	@Override
	public void unbind(Name var0) throws NamingException {
		operationNotSupported();
	}
	
	private void operationNotSupported() throws OperationNotSupportedException {
		throw new OperationNotSupportedException(m_exceptionMessage);
	}

}
