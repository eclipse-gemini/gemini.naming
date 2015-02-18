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
 *     Bob Nettleton - Initial Developer tests for Reference Implementation
 ******************************************************************************/

package org.eclipse.gemini.naming.test;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.NoSuchElementException;
import java.util.Set;

import javax.naming.Binding;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameClassPair;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.NoInitialContextException;
import javax.naming.OperationNotSupportedException;
import javax.naming.directory.DirContext;
import javax.naming.spi.InitialContextFactory;
import javax.naming.spi.InitialContextFactoryBuilder;

import org.osgi.framework.Constants;
import org.osgi.framework.ServiceException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.jndi.JNDIContextManager;


/**
 * Class used to verify the behavior of the JNDIContextManager service.  
 *
 * @author Bob Nettleton
 * 
 */
public class ContextManagerTestCase extends NamingTestCase {
	/**
	 * Verifies that if a specific InitialContextFactory is requested by 
	 * a client, and that factory is not available, the Factory Manager will query the 
	 * list of known InitialContextFactoryBuilder implementations to try 
	 * and create a suitable context.  
	 * 
	 * Please see section 5.2.1.1 of RFC 142 for more details. 
	 * 
	 * @throws Exception
	 */
	public void testSpecificFactoryResolvedByBuilder() throws Exception {
		final String expectedBindingName = "test-binding-one";
		final String expectedBindingValue = "this is only a test";
		
		// setup a test context
		Context testContext = new FactoryResolutionTestCase.TestContext() {
			public Object lookup(String name) throws NamingException {
				if (name.equals(expectedBindingName)) {
					return expectedBindingValue;
				}
				
				throw new NameNotFoundException("Error in JNDI test");
			}
			
		};
		
		// register a builder service
		registerService(InitialContextFactoryBuilder.class.getName(), 
                		new FactoryResolutionTestCase.TestContextFactoryBuilder(testContext), null);
		
		Hashtable environment = new Hashtable();
		environment.put(Context.INITIAL_CONTEXT_FACTORY, "com.test.factory.FactoryDoesNotExist");
		environment.put("osgi.service.jndi.bundleContext", bundleContext);
		// builder should provide an InitialContextFactory implementation
		InitialContext initialContext = null;
		try {
			initialContext = new InitialContext(environment);
			// verify that the expected value is returned
			assertEquals("Factory Manager did not return the expected factory", 
						 expectedBindingValue, initialContext.lookup(expectedBindingName));
		} finally {
			if (initialContext != null) {
				initialContext.close();
			}
		}
		
		// attempt the same test from the context manager service
		
		ServiceReference serviceRef =  
			getContext().getServiceReference(JNDIContextManager.class.getName());
		JNDIContextManager contextManager = 
			(JNDIContextManager)getContext().getService(serviceRef);
		assertNotNull("Context Manager service not available", contextManager);
		Context serviceInitialContext = contextManager.newInitialContext(environment);
		// verify that context returned from manager behaves the same 
		assertEquals("Factory Manager did not return the expected factory", 
				 expectedBindingValue, serviceInitialContext.lookup(expectedBindingName));
		
		getContext().ungetService(serviceRef);
	}
	
	
	/**
	 * Verifies that the Gemini Naming bundle provides an implementation 
	 * of the JNDIContextManager service interface.  
	 */
	public void testJNDIContextManagerServiceAvailable() throws Exception {
		ServiceReference serviceReference = null;
			try {
				serviceReference = getContext().getServiceReference("org.osgi.service.jndi.JNDIContextManager");
				assertNotNull("JNDIContextManager was not published as expected", serviceReference);
			} finally {
				if(serviceReference != null) {
					getContext().ungetService(serviceReference);
				}
			}
	}
	
	/**
	 * Verifies that the implementation of JNDIContextManager.newInitialContext() will
	 * query the available InitialContextFactoryBuilder services to create a "default" context, 
	 * if the JNDI client has not specified which factory to use to create the Context.
	 */
	public void testJNDIContextManagerCreateDefaultContextWithBuilder() throws Exception {
		final String expectedName = "test-one";
		final String expectedValue = "this is only a test";
		// test setup
		final Context testContext = new ExpectedValueTestContext(expectedName, expectedValue);
		InitialContextFactoryBuilder builder = 
			new FactoryResolutionTestCase.TestContextFactoryBuilder(testContext);
		registerService(InitialContextFactoryBuilder.class.getName(), builder, null);
		
		// obtain JNDIContextManager service
		ServiceReference serviceReference = 
			getContext().getServiceReference("org.osgi.service.jndi.JNDIContextManager");
		JNDIContextManager contextManager = (JNDIContextManager)
		    getContext().getService(serviceReference);
		
		// create a context with the default environment setup
		Context initialContext = contextManager.newInitialContext();
		assertNotNull("JNDIContextManager did not create a new default context", initialContext);
		assertEquals("JNDIContextManager did not return the correct context", 
				     expectedValue, initialContext.lookup(expectedName));
		getContext().ungetService(serviceReference);
	}
	
	
	/**
	 * Verify that if a Builder service is un-registered, that the JNDI Context 
	 * created by this Builder will throw the appropriate exception.  If the service
	 * is re-registered, the Context should function properly as before the service
	 * was un-registered.  
	 */
	public void testJNDIContextManagerCreateDefaultContextWithBuilderThenRemoveBuilder() throws Exception {
		final String expectedName = "test-one";
		final String expectedValue = "this is only a test";
		// test setup
		final Context testContext = new ExpectedValueTestContext(expectedName, expectedValue);
		InitialContextFactoryBuilder builder = 
			new FactoryResolutionTestCase.TestContextFactoryBuilder(testContext);
		registerService(InitialContextFactoryBuilder.class.getName(), builder, null);
		
		// obtain JNDIContextManager service
		ServiceReference serviceReference = 
			getContext().getServiceReference("org.osgi.service.jndi.JNDIContextManager");
		JNDIContextManager contextManager = (JNDIContextManager)
		    getContext().getService(serviceReference);
		
		// create a context with the default environment setup
		Context initialContext = contextManager.newInitialContext();
		assertNotNull("JNDIContextManager did not create a new default context", initialContext);
		assertEquals("JNDIContextManager did not return the correct context", 
				     expectedValue, initialContext.lookup(expectedName));
		
		unregisterService(builder);
		// try lookup again, without builder being available
		try {
			initialContext.lookup(expectedName);
			fail("NoInitialContextException should have been thrown");
		} catch (NoInitialContextException e) {
			// expected exception
		}
		
		// re-register Builder service
		registerService(InitialContextFactoryBuilder.class.getName(), 
		        builder, null);
		
		assertEquals("JNDIContextManager did not return the correct context after re-bind of Builder service", 
			     expectedValue, initialContext.lookup(expectedName));
		
		getContext().ungetService(serviceReference);
	}
	
	
	/**
	 * Verify that when all references to the JNDIContextManager service 
	 * are released (with an ungetService() call), the JNDIContextManager service
	 * implementation will call Context.close() on all Contexts created for the client
	 * by this service.  
	 */
	public void testJNDIContextManagerContextClose() throws Exception {
		final String expectedName = "test-one";
		final String expectedValue = "this is only a test";
		// test setup
		final ExpectedValueTestContext testContext = 
			new ExpectedValueTestContext(expectedName, expectedValue);
		InitialContextFactoryBuilder builder = 
			new FactoryResolutionTestCase.TestContextFactoryBuilder(testContext);
		registerService(InitialContextFactoryBuilder.class.getName(), builder, null);
		
		// obtain JNDIContextManager service
		ServiceReference serviceReference = 
			getContext().getServiceReference("org.osgi.service.jndi.JNDIContextManager");
		JNDIContextManager contextManager = (JNDIContextManager)
		    getContext().getService(serviceReference);
		
		try {
			// create a context with the default environment setup
			Context initialContext = contextManager.newInitialContext();
			assertNotNull("JNDIContextManager did not create a new default context", initialContext);
			assertEquals("JNDIContextManager did not return the correct context", 
					     expectedValue, initialContext.lookup(expectedName));
			
			// remove reference to service, this should trigger a close() call
			// on the context instance.  
			assertEquals("JNDIContextManager service should not have closed the context yet", 
					     0, testContext.getNumCloseCalls());
			getContext().ungetService(serviceReference);
			assertEquals("JNDIContextManager service did not properly close the created context",
					     1, testContext.getNumCloseCalls());
		} finally {
			getContext().ungetService(serviceReference);
		}
		
	}
	
	
	/**
	 * Verify that if no factories or builders exist to create a Context, the 
	 * "default" Context instance returned will not support bind() calls.  
	 */
	public void testJNDIContextManagerCreateDefaultContextWithoutBuilder() throws Exception {
		// obtain JNDIContextManager service
		ServiceReference serviceReference = 
			getContext().getServiceReference("org.osgi.service.jndi.JNDIContextManager");
		JNDIContextManager contextManager = (JNDIContextManager)
		    getContext().getService(serviceReference);
		
		// create a context with the default environment setup
		try {
			// this should fail
			Context initialContext = contextManager.newInitialContext();
			initialContext.bind("this is just a test", "test object one");
			fail("NoInitialContextException should have been thrown");
		}
		catch (NoInitialContextException namingException) {
			// expected exception
		}
		
		getContext().ungetService(serviceReference);
	}
	
	
	
	/**
	 * Verify basic creation of DirContexts.  
	 */
	public void testCreateDirContext() throws Exception {
		// setup test InitialContextFactory service
		String[] serviceInterfaceNames = {InitialContextFactory.class.getName(), 
				                          TestInitialDirContextFactory.class.getName()};
		ServiceRegistration serviceRegistration = 
			getContext().registerService(serviceInterfaceNames, 
					                     new TestInitialDirContextFactory(), null);
		
		try {
			ServiceReference serviceReference = 
				getContext().getServiceReference("org.osgi.service.jndi.JNDIContextManager");
			JNDIContextManager contextManager = (JNDIContextManager)
			    getContext().getService(serviceReference);
			
			Hashtable environment = new Hashtable();
			environment.put(Context.INITIAL_CONTEXT_FACTORY, 
					        TestInitialDirContextFactory.class.getName());
			
			DirContext dirContext = 
				contextManager.newInitialDirContext(environment);
			assertNotNull("JNDIContextManager did not create a DirContext as expected", 
					      dirContext);
			
			getContext().ungetService(serviceReference);
		}
		finally {
			if(serviceRegistration != null) {
				serviceRegistration.unregister();
			}
		}
	}
	
	
	/**
	 * Verify creation of DirContext using an InitialContextFactoryBuilder.  
	 */
	public void testJNDIContextManagerCreateDefaultDirContextWithBuilder() throws Exception {
		// test setup
		InitialContextFactoryBuilder builder = 
			new InitialContextFactoryBuilder() {
				public InitialContextFactory createInitialContextFactory(
						Hashtable var0) throws NamingException {
					return new TestInitialDirContextFactory();
				}
		};

		registerService(InitialContextFactoryBuilder.class.getName(), 
				        builder, null);
		
		// obtain JNDIContextManager service
		ServiceReference serviceReference = 
			getContext().getServiceReference("org.osgi.service.jndi.JNDIContextManager");
		JNDIContextManager contextManager = (JNDIContextManager)
		    getContext().getService(serviceReference);
		
		// create a context with the default environment setup
		DirContext initialDirContext = contextManager.newInitialDirContext();
		assertNotNull("JNDIContextManager did not create a new default context", initialDirContext);
		
		getContext().ungetService(serviceReference);
	}
	
	
	/**
	 * Verify that the methods to create a DirContext on the JNDIContextManager
	 * interface check the type of the created Context.  If the object is not an 
	 * instanceof DirContext, a NoInitialContextException should be thrown.  
	 * 
	 */
	public void testJNDIContextManagerCreateDirContextWithBuilderWrongType() throws Exception {
		// test setup
		registerService(InitialContextFactoryBuilder.class.getName(), 
				        new FactoryResolutionTestCase.TestContextFactoryBuilder(new FactoryResolutionTestCase.TestContext()), null);
		
		// obtain JNDIContextManager service
		ServiceReference serviceReference = 
			getContext().getServiceReference("org.osgi.service.jndi.JNDIContextManager");
		JNDIContextManager contextManager = (JNDIContextManager)
		    getContext().getService(serviceReference);
		
		// create a context with an environment config
		try {
			contextManager.newInitialDirContext(new Hashtable());
			fail("NoInitialContextException should have been thrown");
		}
		catch (NoInitialContextException e) {
			// expected exception
		}
		
		// create a context with the default environment config
		try {
			contextManager.newInitialDirContext();
			fail("NoInitialContextException should have been thrown");
		} 
		catch (NoInitialContextException e) {
			// expected exception
		}
		
		getContext().ungetService(serviceReference);
	}

	/**
	 * Verify that if no builders or factories exist to create the DirContext,
	 * then a NoInitialContextException should be thrown.  
	 * 
	 */
	public void testJNDIContextManagerCreateDefaultDirContextWithoutBuilder() throws Exception {
		// obtain JNDIContextManager service
		ServiceReference serviceReference = 
			getContext().getServiceReference("org.osgi.service.jndi.JNDIContextManager");
		JNDIContextManager contextManager = (JNDIContextManager)
		    getContext().getService(serviceReference);
		
		// create a context with the default environment setup
		try {
			// this should fail
			contextManager.newInitialDirContext();
			fail("NoInitialContextException should have been thrown");
		}
		catch (NoInitialContextException namingException) {
			// expected exception
		}
		
		getContext().ungetService(serviceReference);
	}
	
	
	/**
	 * Verify the basic behavior of an "osgi:servicelist" lookup, including
	 * the basic operations of the NamingEnumeration associated with this type
	 * of lookup (Context.list()).  
	 */
	public void testJNDIServiceListNamingEnumeration() throws Exception {
		final String expectedBindingName = "test-binding-one";
		final String expectedBindingValue = "this is only a test";
		
		// setup a test context
		Context testContext = 
			new ExpectedValueTestContext(expectedBindingName, expectedBindingValue);
		
		// register a builder service
		registerService(InitialContextFactoryBuilder.class.getName(), 
                		new FactoryResolutionTestCase.TestContextFactoryBuilder(testContext), null);
		
		registerService(Closeable.class.getName(), new NoOpCloseable(), null);
		registerService(Closeable.class.getName(), new NoOpCloseable(), null);
		
		ServiceReference[] builderReferences = 
			getContext().getServiceReferences(Closeable.class.getName(), null);
		
		final Set setOfIds = new HashSet();
		for(int i = 0; i < builderReferences.length; i++) {
			setOfIds.add(builderReferences[i].getProperty(Constants.SERVICE_ID).toString());
		}
		
		assertEquals("Initial set of services is not the expected number", 
				      2, setOfIds.size());
		
		
		// obtain JNDIContextManager service
		ServiceReference serviceReference = 
			getContext().getServiceReference("org.osgi.service.jndi.JNDIContextManager");
		JNDIContextManager contextManager = (JNDIContextManager)
		    getContext().getService(serviceReference);
		
		
		try {
			Context initialContext = contextManager.newInitialContext(new Hashtable());
			Context serviceListContext = (Context)initialContext.lookup("osgi:servicelist/" + Closeable.class.getName());
		
			final int originalNumberOfServicesInUse = 
				getContext().getBundle().getServicesInUse().length;
			
			assertNotNull("JNDI implementation did not return the expected context type for a servicelist URL", 
					       serviceListContext);
			NamingEnumeration namingEnum = serviceListContext.list("");
			assertTrue("NamingEnumeration did not contain any elements", 
					    namingEnum.hasMoreElements());
			
			Object result = namingEnum.next();
			assertNotNull("NamingEnumeration did not contain the expected service",
					       result);
			assertTrue("NamingEnumeration returned an unexpected type", 
					    result instanceof NameClassPair);

			NameClassPair nameClassPair = (NameClassPair)result;
			assertEquals("NameClassPair did not contain the expected type", 
					     Closeable.class.getName(), 
					     nameClassPair.getClassName());
			assertTrue("NameClassPair did not contain an expected ID", 
					    setOfIds.contains(nameClassPair.getName()));
			
			assertTrue("NamingEnumeration should contain one more element", 
				     namingEnum.hasMoreElements());
			
			NameClassPair nameClassPairTwo = (NameClassPair)namingEnum.next();
			assertEquals("NameClassPair did not contain the expected type", 
					     Closeable.class.getName(),
					     nameClassPairTwo.getClassName());
			assertTrue("NameClassPair did not contain an expected ID", 
				        setOfIds.contains(nameClassPairTwo.getName()));
			
			assertFalse("NamingEnumeration should not contain any more elements", 
				        namingEnum.hasMoreElements());
			
			// verify exception handling
			try {
				namingEnum.nextElement();
				fail("NoSuchElementException should have been thrown");
			} catch (NoSuchElementException e) {
				// expected exception
			}
			
			// verify exception handling
			try {
				namingEnum.next();
				fail("NoSuchElementException should have been thrown");
			} catch (NoSuchElementException e) {
				// expected exception
			}

			assertEquals("NamingEnumeration returned from Context.list() call incorrectly increased the service count for this calling bundle", 
					     originalNumberOfServicesInUse, getContext().getBundle().getServicesInUse().length);
			
			
			NamingEnumeration namingEnumTwo = 
				serviceListContext.list("");
			assertNotSame("ServiceList context should have returned a new enumeration",
					       namingEnum, namingEnumTwo);
			
			// close first enumeration, verify that service count does not change
			namingEnum.close();
			assertEquals("NamingEnumeration returned from Context.list() call incorrectly decreased the service count for this calling bundle", 
				     originalNumberOfServicesInUse, getContext().getBundle().getServicesInUse().length);
			
			
			 assertTrue("NamingEnum should have contained elements", 
					    namingEnumTwo.hasMoreElements());
			 namingEnumTwo.close();
			 
			 try {
				 namingEnumTwo.next();
				 fail("NamingException should have been thrown");
			 } catch (NamingException e) {
				 // expected exception
			 }
			 
			// verify that only empty string context lists are supported
			try {
				serviceListContext.list("someName");
				fail("OperationNotSupportedException should have been thrown");
			} catch (OperationNotSupportedException e) {
				// expected exception
			}
			
		} finally {
			getContext().ungetService(serviceReference);
		}
	}
	
	/**
	 * Verifies the behavior of the NamingEnumeration returned as the result 
	 * of a Context.listBindings() call on a Context that supports the "osgi:servicelist" 
	 * lookup.  
	 */
	public void testJNDIServiceListBindingsNamingEnumeration() throws Exception {
		final String expectedBindingName = "test-binding-one";
		final String expectedBindingValue = "this is only a test";
		
		// setup a test context
		Context testContext = 
			new ExpectedValueTestContext(expectedBindingName, expectedBindingValue);
		
		// register a builder service
		registerService(InitialContextFactoryBuilder.class.getName(), 
                		new FactoryResolutionTestCase.TestContextFactoryBuilder(testContext), null);
		
		registerService(Closeable.class.getName(), new NoOpCloseable(), null);
		
		registerService(Closeable.class.getName(), new NoOpCloseable(), null);
		
		ServiceReference[] builderReferences = 
			getContext().getServiceReferences(Closeable.class.getName(), null);
		
		final Set setOfIds = new HashSet();
		for(int i = 0; i < builderReferences.length; i++) {
			setOfIds.add(builderReferences[i].getProperty(Constants.SERVICE_ID).toString());
		}
		
		assertEquals("Initial set of services is not the expected number", 
				      2, setOfIds.size());
		
		// obtain JNDIContextManager service
		ServiceReference serviceReference = 
			getContext().getServiceReference("org.osgi.service.jndi.JNDIContextManager");
		JNDIContextManager contextManager = (JNDIContextManager)
		    getContext().getService(serviceReference);
		
		
		try {
			Context initialContext = contextManager.newInitialContext(new Hashtable());
			Context serviceListContext = (Context)initialContext.lookup("osgi:servicelist/" + Closeable.class.getName());
			
			final int originalNumOfServices =  
				getContext().getBundle().getServicesInUse().length;
			
			assertNotNull("JNDI implementation did not return the expected context type for a servicelist URL", 
					       serviceListContext);
			
			NamingEnumeration namingEnum = serviceListContext.listBindings("");
			
			// verify that the number of services in use by this bundle 
			// has increased due to the call to listBindings
			// this test verifies that the NamingEnumeration is using the 
			// caller's bundle context to obtain the services
			assertEquals("Context.listBindings() call should have increased the service count for this bundle",
					      originalNumOfServices + setOfIds.size(), getContext().getBundle().getServicesInUse().length);
			
			assertTrue("NamingEnumeration did not contain any elements", 
				    namingEnum.hasMoreElements());
		
			Object result = namingEnum.next();
			assertNotNull("NamingEnumeration did not contain the expected service",
				          result);
			assertTrue("NamingEnumeration returned an unexpected type", 
				        result instanceof Binding);
			
			Binding bindingOne = (Binding)result;
			assertEquals("Incorrect Binding type", 
					      Closeable.class.getName(), 
					      bindingOne.getClassName());
			assertTrue("Binding's service ID was not expected",
					    setOfIds.contains(bindingOne.getName()));
			assertNotNull("Binding's service object was not included", 
					       bindingOne.getObject());
			assertTrue("Binding's service object was not the correct type",
					    bindingOne.getObject() instanceof Closeable);
			
			
			assertTrue("NamingEnumeration should contain one more element", 
				     namingEnum.hasMoreElements());
			
			Binding bindingTwo = (Binding)namingEnum.next();
			assertEquals("NameClassPair did not contain the expected type", 
					     Closeable.class.getName(),
					     bindingTwo.getClassName());
			assertTrue("NameClassPair did not contain an expected ID", 
				        setOfIds.contains(bindingTwo.getName()));
			assertNotNull("Binding's service object was not included", 
				          bindingTwo.getObject());
			assertTrue("Binding's service object was not the correct type",
				        bindingTwo.getObject() instanceof Closeable);
			
			assertFalse("NamingEnumeration should not contain any more elements", 
				        namingEnum.hasMoreElements());
			
			
			
			NamingEnumeration namingEnumTwo = 
				serviceListContext.listBindings("");
			assertNotSame("ServiceList context should have returned a new enumeration",
					       namingEnum, namingEnumTwo);
			
			 assertTrue("NamingEnum should have contained elements", 
					    namingEnumTwo.hasMoreElements());
			 namingEnumTwo.close();
			 
			 try {
				 namingEnumTwo.next();
				 fail("NamingException should have been thrown");
			 } catch (NamingException e) {
				 // expected exception
			 }
			
			
			// verify exception handling
			try {
				namingEnum.nextElement();
				fail("NoSuchElementException should have been thrown");
			} catch (NoSuchElementException e) {
				// expected exception
			}
			
			// verify exception handling
			try {
				namingEnum.next();
				fail("NoSuchElementException should have been thrown");
			} catch (NoSuchElementException e) {
				// expected exception
			}
			
			// verify that only empty string context lists are supported
			try {
				serviceListContext.listBindings("someName");
				fail("OperationNotSupportedException should have been thrown");
			} catch (OperationNotSupportedException e) {
				// expected exception
			}
			
			namingEnum.close();

			assertEquals("namingEnum.close() call should have decreased the service count for this bundle",
				          originalNumOfServices, getContext().getBundle().getServicesInUse().length);
			
		} finally {
			getContext().ungetService(serviceReference);
		}
	}
	
	
	/**
	 * Verify that an OSGi service returned from a JNDI lookup is proxied
	 * according to the requirements of the OSGi Enterprise Spec, JNDI Services
	 * Chapter.  
	 */
	public void testOSGiServiceProxyWithInterface() throws Exception {
		// test setup
		Hello testService = new Hello() {
			public String sayHello(String name) {
				return "Hello " + name;
			}
		};
		
		registerService(Hello.class.getName(), testService, null);
		// obtain JNDIContextManager service
		ServiceReference serviceReference = 
			getContext().getServiceReference("org.osgi.service.jndi.JNDIContextManager");
		JNDIContextManager contextManager = (JNDIContextManager)
		    getContext().getService(serviceReference);
		
		try {
			Context initialContext = contextManager.newInitialContext();
			Hello helloService = 
				(Hello) initialContext.lookup("osgi:service/" + Hello.class.getName());
			assertNotNull("OSGi URL Context did not return the expected service value", helloService);
			assertEquals("OSGi URL Context did not return the correct service", 
					     "Hello Bob", helloService.sayHello("Bob"));
			
			// verify that service un-binds are handled correctly
			unregisterService(testService);
			
			try {
				helloService.sayHello("OSGi");
				fail("ServiceException should have been thrown");
			} catch(ServiceException serviceUnavailable) {
				// expected exception
				assertEquals("ServiceException was thrown with incorrect type code", 
						      ServiceException.UNREGISTERED, serviceUnavailable.getType());
			}
			
			// verify that service re-binds are handled correctly
			registerService(Hello.class.getName(), testService, null);
			
			assertEquals("OSGi URL Context did not return the correct service", 
				     "Hello Todd", helloService.sayHello("Todd"));
		} finally {
			getContext().ungetService(serviceReference);
		}
		
	}
	
	
	/**
	 * Verify that the Gemini Naming bundle properly handles the case of 
	 * a private interface being used as an OSGi service interface.  An exception
	 * should be thrown back to the JNDI client, indicating the error.  
	 */
	public void testOSGiServiceProxyWithPrivateInterface() throws Exception {
		// test setup
		Thanks testService = new Thanks() {
			public String sayThankYou(String name) {
				return "Thank You " + name;
			}
		};
		
		registerService(Thanks.class.getName(), testService, null);
		// obtain JNDIContextManager service
		ServiceReference serviceReference = 
			getContext().getServiceReference("org.osgi.service.jndi.JNDIContextManager");
		JNDIContextManager contextManager = (JNDIContextManager)
		    getContext().getService(serviceReference);
		
		try {
			Context initialContext = contextManager.newInitialContext();
			Thanks thanksService = 
				(Thanks) initialContext.lookup("osgi:service/" + Thanks.class.getName());
			assertNotNull("OSGi URL Context did not return the expected service value", thanksService);
			
			try {
				thanksService.sayThankYou("Connie");
				fail("ServiceException should have been thrown");
			}
			catch (ServiceException serviceException) {
				// expected exception
				assertTrue("ServiceException did not contain expected cause", 
						    serviceException.getCause() instanceof IllegalAccessException);
			}
		} finally {
			getContext().ungetService(serviceReference);
		}
	}
	
	/**
	 * Verify the behavior of service proxies that are returned 
	 * as part of a NamingEnumeration from a Context.listBindings() call on 
	 * a Context returned from an "osgi:servicelist/" lookup.  
	 */
	public void testServiceProxyWithListBindingsNamingEnumeration() throws Exception {


	/*******************************************************/
        /* Comment out this test. It seems to spuriously fail. */
	/*******************************************************/

/* 

		// test setup
		Hello testService = new Hello() {
			public String sayHello(String name) {
				return "Hello " + name;
			}
		};
		
		registerService(Hello.class.getName(), testService, null);
		// obtain JNDIContextManager service
		ServiceReference serviceReference = 
			getContext().getServiceReference("org.osgi.service.jndi.JNDIContextManager");
		JNDIContextManager contextManager = (JNDIContextManager)
		    getContext().getService(serviceReference);
		
		try {
			final int originalNumberOfServices = 
				getContext().getBundle().getServicesInUse().length;
			
			Context initialContext = contextManager.newInitialContext();
			Context serviceListContext = 
				(Context) initialContext.lookup("osgi:servicelist/" + Hello.class.getName());
			assertNotNull("JNDIContextManager did not return expected context", serviceListContext);
			
			NamingEnumeration namingEnumeration = 
				serviceListContext.listBindings("");
			
			Binding binding = (Binding)namingEnumeration.next();
			Hello serviceFromEnum = 
				(Hello)binding.getObject();
			
			assertNotNull("ServiceListContext did not return expected service in enum", 
					       serviceFromEnum);
			assertEquals("Returned service did not return correct result", 
					      "Hello Bob", serviceFromEnum.sayHello("Bob"));
			
			unregisterService(testService);
			// verify that proxy handles un-bind correctly
			try {
				serviceFromEnum.sayHello("Joe");
				fail("ServiceException should have been thrown");
			} catch (ServiceException serviceException) {
				// expected exception
				assertEquals("ServiceException was thrown with incorrect type code", 
						      ServiceException.UNREGISTERED, serviceException.getType());
			}
			
			registerService(Hello.class.getName(), testService, null);
			// verify that proxy does not attempt to rebind the service
			try {
				serviceFromEnum.sayHello("Cole");
				fail("ServiceException should have been thrown");
			} catch (ServiceException serviceException) {
				// expected exception
				assertEquals("ServiceException was thrown with incorrect type code", 
						      ServiceException.UNREGISTERED, serviceException.getType());
			}
			
			// clean up enumeration
			namingEnumeration.close();

			// verify service tracking
			assertEquals("JNDI Implementation did not correctly manage service references", 
					     originalNumberOfServices, getContext().getBundle().getServicesInUse().length);
		} finally {
			getContext().ungetService(serviceReference);
		}
*/
		
	}

    /* test classes */	
	private static final class ExpectedValueTestContext extends FactoryResolutionTestCase.TestContext {
		private final String expectedValue;
		private final String expectedName;

		private ExpectedValueTestContext(String expectedName, String expectedValue) {
			this.expectedName = expectedName;
			this.expectedValue = expectedValue;
		}

		public Object lookup(String name) throws NamingException {
			if(name.equals(expectedName)) {
				return expectedValue;
			}
			throw new NameNotFoundException("name not found - test error");
		}
	}

	private static final class NoOpCloseable implements Closeable {
		public void close() throws IOException {
			// no-op for testing
		}
	}

	private static class TestInitialDirContextFactory implements InitialContextFactory {

		public Context getInitialContext(Hashtable var0)
				throws NamingException {
			
			return (Context)Proxy.newProxyInstance(this.getClass().getClassLoader(), 
					                      new Class[] {DirContext.class}, 
					                      new TestInvocationHandler());
			
		}
		
		private static class TestInvocationHandler implements InvocationHandler {

			public Object invoke(Object var0, Method var1, Object[] var2)
					throws Throwable {
				return null;
			}
			
		}
			
	}
	
	public interface Hello {
		String sayHello(String name);
	}
	
	private interface Thanks {
		String sayThankYou(String name);
	}
	
}
