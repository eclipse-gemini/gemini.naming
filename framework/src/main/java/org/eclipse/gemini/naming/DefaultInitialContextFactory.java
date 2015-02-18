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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.NoInitialContextException;
import javax.naming.spi.InitialContextFactory;

/**
 * This class implements a default InitialContextFactory.  
 * 
 * The Context implementation created by this factory is a no-op
 * implementation of javax.naming.Context. 
 *
 * 
 * @version $Revision$
 */
class DefaultInitialContextFactory implements InitialContextFactory {

	@Override
	public Context getInitialContext(Hashtable environment) throws NamingException {
		return (Context) Proxy.newProxyInstance(this.getClass().getClassLoader(),
				                                new Class[] {Context.class}, 
				                                new DefaultContextInvocationHandler());
	}
	
	
	/**
	 * InvocationHandler for the default Context.  Except for close() and getEnvironment(), 
	 * all Context method invocations should throw a NoInitialContextFactory exception.  
	 *
	 * 
	 * @version $Revision$
	 */
	private static class DefaultContextInvocationHandler implements InvocationHandler {

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			// special case for close() invocation
			if(method.getName().equals("close")) {
				return null;
			}
			
			// special case for getEnvironment(), return empty Hashtable
			if(method.getName().equals("getEnvironment")) {
				return new Hashtable();
			}
			
			throw new NoInitialContextException("No InitialContext service available to handle this request");
		}
	}
}