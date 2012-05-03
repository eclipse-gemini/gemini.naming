/*******************************************************************************
 * Copyright (c) 2010 Oracle.
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
 *     Bob Nettleton (Oracle) - Initial Reference Implementation Unit Tests
 ******************************************************************************/

package org.eclipse.gemini.naming;

import java.lang.reflect.Field;
import java.util.Dictionary;
import java.util.Hashtable;

import javax.naming.NamingException;
import javax.naming.spi.DirObjectFactory;
import javax.naming.spi.InitialContextFactory;
import javax.naming.spi.InitialContextFactoryBuilder;
import javax.naming.spi.NamingManager;
import javax.naming.spi.ObjectFactory;
import javax.naming.spi.ObjectFactoryBuilder;

import org.easymock.EasyMockSupport;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.jndi.JNDIConstants;
import org.osgi.service.jndi.JNDIContextManager;
import org.osgi.service.jndi.JNDIProviderAdmin;

import static org.easymock.EasyMock.*;

import junit.framework.TestCase;

public class ActivatorTestCase extends TestCase {

	public void setUp() {
		setNamingManagerStaticFieldToNull("initctx_factory_builder");
		setNamingManagerStaticFieldToNull("object_factory_builder");
	}

	public void tearDown() {
		setNamingManagerStaticFieldToNull("initctx_factory_builder");
		setNamingManagerStaticFieldToNull("object_factory_builder");
	}
	
	
	/**
	 * Verify the basic startup of the Gemini Naming Activator.  
	 *  
	 * This test method verifies that all the expected services are 
	 * registered using the correct interface, actual type, and service
	 * properties.  
	 */
	public void testStart() throws Exception {
		EasyMockSupport mockSupport = new EasyMockSupport();
		BundleContext bundleContextMock = 
			mockSupport.createMock(BundleContext.class);
		
		setupBundleContextMock(mockSupport, bundleContextMock);
		// expect OSGi URLContextFactory 
		Hashtable<String, Object> serviceProperties = new Hashtable<String, Object>();
		serviceProperties.put(JNDIConstants.JNDI_URLSCHEME, "osgi");
		setServiceRegistrationExpectation(mockSupport, bundleContextMock,
				                          ObjectFactory.class.getName(), 
				                          OSGiURLContextFactoryServiceFactory.class, 
				                          serviceProperties);

		// expect the "default" InitialContextFactoryBuilder
		setServiceRegistrationExpectation(mockSupport, bundleContextMock,
				                          InitialContextFactoryBuilder.class.getName(),
				                          DefaultRuntimeInitialContextFactoryBuilder.class,
				                          null);
		
		// expect the JNDIContextManager service registration
		setServiceRegistrationExpectation(mockSupport, bundleContextMock, 
				                          JNDIContextManager.class.getName(),
				                          ContextManagerServiceFactoryImpl.class, 
                						  null);
		// expect the JNDIProviderAdmin service registration
		setServiceRegistrationExpectation(mockSupport, bundleContextMock, 
                						  JNDIProviderAdmin.class.getName(),
                						  SecurityAwareProviderAdminImpl.class, 
                						  null);
		// expect the rmiURLContextFactory service registration
		Hashtable<String, Object> props = new Hashtable<String, Object>();
        props.put(JNDIConstants.JNDI_URLSCHEME, "rmi");
		setServiceRegistrationExpectation(mockSupport, bundleContextMock, 
										  ObjectFactory.class.getName(),
										  ClassLoader.getSystemClassLoader().loadClass("com.sun.jndi.url.rmi.rmiURLContextFactory"), 
                						  props);				
		
		mockSupport.replayAll();
		
		// begin test
		Activator activator = new Activator();
		activator.start(bundleContextMock);
		
		// verify that the static singletons are set as expected
		assertTrue("Activator did not set the InitialContextFactoryBuilder singleton correctly",
				    getPrivateStaticField(NamingManager.class, "initctx_factory_builder") instanceof TraditionalInitialContextFactoryBuilder);
		assertTrue("Activator did not set the ObjectFactoryBuilder singleton correctly",
			       getPrivateStaticField(NamingManager.class, "object_factory_builder") instanceof TraditionalObjectFactoryBuilder);
		
		// stop() should complete without any exceptions
		activator.stop(bundleContextMock);
		
		mockSupport.verifyAll();
	}
	
	public void testRegistrationOfContextBuilderError() throws Exception {
		// setup test mocks
		EasyMockSupport mockSupport = new EasyMockSupport();
		InitialContextFactoryBuilder builderMock = 
			mockSupport.createMock(InitialContextFactoryBuilder.class);
		if(!NamingManager.hasInitialContextFactoryBuilder()) {
			NamingManager.setInitialContextFactoryBuilder(builderMock);
		}
		
		BundleContext bundleContextMock = 
			mockSupport.createMock(BundleContext.class);
		mockSupport.replayAll();
		
		// begin test
		Activator activator = new Activator();
		try {
			activator.start(bundleContextMock);
			fail("NamingException should have been thrown");
		} catch (NamingException namingException) {
			// expected exception
		}
		
		mockSupport.verifyAll();
		
	}
	
	
	public void testRegistrationOfObjectFactoryBuilderError() throws Exception {
		// setup test mocks
		EasyMockSupport mockSupport = new EasyMockSupport();
		ObjectFactoryBuilder builderMock = 
			mockSupport.createMock(ObjectFactoryBuilder.class);
		
		try {
			// try to set builder, to guarantee
			// that Activator's attempt to set this will fail
			NamingManager.setObjectFactoryBuilder(builderMock);
		} catch (Throwable throwable) {
			// if already set, test can continue
		}
		
		BundleContext bundleContextMock = 
			mockSupport.createMock(BundleContext.class);
		mockSupport.replayAll();
		
		// begin test
		Activator activator = new Activator();
		try {
			activator.start(bundleContextMock);
			fail("NamingException should have been thrown");
		} catch (NamingException namingException) {
			// expected exception
		}
		
		mockSupport.verifyAll();
		
	}

	/* test utility methods/classes */
	
	private static void setupBundleContextMock(EasyMockSupport mockSupport, BundleContext bundleContextMock) throws InvalidSyntaxException {
		Filter filterMock = 
			mockSupport.createMock(Filter.class);
		expect(bundleContextMock.createFilter("(objectClass=" + InitialContextFactory.class.getName() + ")")).andReturn(filterMock);
		expect(bundleContextMock.createFilter("(objectClass=" + InitialContextFactoryBuilder.class.getName() + ")")).andReturn(filterMock);
		expect(bundleContextMock.createFilter("(objectClass=" + ObjectFactory.class.getName() + ")")).andReturn(filterMock).anyTimes();
		expect(bundleContextMock.createFilter("(objectClass=" + DirObjectFactory.class.getName() + ")")).andReturn(filterMock);
		expect(bundleContextMock.createFilter("(objectClass=" + ObjectFactoryBuilder.class.getName() + ")")).andReturn(filterMock);
		
		bundleContextMock.addServiceListener(isA(ServiceListener.class), isA(String.class));
		expectLastCall().anyTimes();
		
		bundleContextMock.removeServiceListener(isA(ServiceListener.class));
		expectLastCall().anyTimes();
		
		expect(bundleContextMock.getServiceReferences(isA(String.class), isA(String.class))).andReturn(new ServiceReference[0]).anyTimes();
		expect(bundleContextMock.getServiceReferences(isA(String.class), (String)isNull())).andReturn(new ServiceReference[0]).anyTimes();
	}

	private static <T> void setServiceRegistrationExpectation(EasyMockSupport mockSupport, BundleContext bundleContextMock, String serviceName, Class<T> serviceType, Dictionary<String, Object> serviceProperties) {
		ServiceRegistration serviceRegistrationMock = 
			mockSupport.createMock(ServiceRegistration.class);
		// required for stop() method
		serviceRegistrationMock.unregister();
		if(serviceProperties != null) {
			expect(bundleContextMock.registerService(eq(serviceName),
	                isA(serviceType), eq(serviceProperties))).andReturn(serviceRegistrationMock);
		} else {
			expect(bundleContextMock.registerService(eq(serviceName),
	                isA(serviceType), (Dictionary)isNull())).andReturn(serviceRegistrationMock);
		}
		
	}
	
	/**
	 * This method should be used only for unit testing the Gemini Naming Activator.  
	 * 
	 * This method sets the static singleton fields on the NamingManger class to null.  This facilitates
	 * simpler unit-testing of the Activator, which attempts to set these singletons.  The NamingManager usually
	 * throws and Exception on the set() methods for these singletons after they've been set once.  
	 * 
	 * This utility method is meant to make unit testing simpler, but should not be used in production code. 
	 * 
	 * This utility method will probably not work properly if a security manager is enabled.  
	 * 
	 * @param fieldName the name of the static field to set to null.
	 */
	private static void setNamingManagerStaticFieldToNull(String fieldName) {
		try {
			final Field field = NamingManager.class.getDeclaredField(fieldName);
			if(field != null) {
				field.setAccessible(true);
				// reset this static field to null
				field.set(null, null);
			}
		} catch (Throwable throwable) {
			throwable.printStackTrace();
		}
	}
	
	/*
	 * Utility method used to access the private static singletons on the NamingManager class.  
	 * 
	 * This method should only be used for unit-testing the Gemini Naming code.  
	 *
	 * This utility method will probably not work properly if a security manager is enabled.
	 * 
	 * @param type the Class type that contains the field
	 * @param fieldName a String name that identifies the field
	 */
	private static Object getPrivateStaticField(Class type, String fieldName) throws Exception {
		Field field = type.getDeclaredField(fieldName);
		if(field != null) {
			field.setAccessible(true);
			return field.get(null);
		}
		
		return null;
	}
	
}
