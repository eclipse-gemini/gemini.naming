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

import java.lang.reflect.Field;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.NamingException;
import javax.naming.spi.InitialContextFactoryBuilder;
import javax.naming.spi.NamingManager;
import javax.naming.spi.ObjectFactory;
import javax.naming.spi.ObjectFactoryBuilder;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.jndi.JNDIConstants;
import org.osgi.service.jndi.JNDIContextManager;
import org.osgi.service.jndi.JNDIProviderAdmin;

/**
 * Activator implementation for the Gemini Naming Bundle.
 * 
 * This activator's main purpose is to register the JNDI Builder singleton
 * implementations that allow the Factory Manager to override the default JNDI
 * framework.
 * 
 * 
 */
public class Activator implements BundleActivator {

	private static final String					OSGI_URL_SCHEME					= "osgi";
	private static final String					RMI_URL_SCHEME					= "rmi";
	private static final String					RMI_URL_CONTEXT_FACTORY			= "com.sun.jndi.url.rmi.rmiURLContextFactory";
	
	private static Logger logger = Logger.getLogger(Activator.class.getName());

	private BundleContext						m_bundleContext					= null;
	private final List<ServiceRegistration>        m_listOfServiceRegistrations = new LinkedList<ServiceRegistration>();

	private CloseableProviderAdmin	m_providerAdminService;
	private ContextManagerServiceFactoryImpl m_contextManagerServiceFactory;
	
	/*
	 * Create the Factory Manager's builder implementation, and register it with
	 * the JNDI NamingManager.
	 * 
	 * @see
	 * org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext
	 * )
	 */
	@Override
	public void start(BundleContext context) throws Exception {
		logger.info("Initializing Gemini Naming Factory Manager Bundle");
		
		m_bundleContext = context;

		// register static singletons with the JNDI framework
		logger.info("Installing Static Singletons");
		registerInitialContextFactoryBuilderSingleton();
		registerObjectFactoryBuilderSingleton();

		logger.info("Registering URL Context Factory for 'osgi' URL scheme");
		registerOSGiURLContextFactory();
		
		logger.info("Registering Default Runtime Builder for JRE-provided factories");
		registerDefaultRuntimeBuilder();
		
		logger.info("Registering ContextManager service");
		// register the JNDIContextManager service once all Factory
		// Manager initialization is complete
		registerContextManager();
		
		logger.info("Registering ProviderAdmin service");
		// register the JNDIProviderAdmin interface, used by OSGi-aware
		// context implementations to resolve JNDI references
		registerProviderAdmin();
	}
	

	/*
	 * Allow the Builder implementation to clean up any
	 * ServiceListener/ServiceTracker instances.
	 * 
	 * @see
	 * org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
	 */
	@Override
	public void stop(BundleContext context) throws Exception {
		logger.info("Shutting down Gemini Naming Factory Manager Bundle");
		
		// close all known Contexts associated with the JNDIContextManager service
		m_contextManagerServiceFactory.closeAll();
		
		// close the JNDIProviderAdmin service
		m_providerAdminService.close();

		// unregister all the JNDI services registered by this Activator
		Iterator<ServiceRegistration> iterator = m_listOfServiceRegistrations.iterator();
		while(iterator.hasNext()) {
			ServiceRegistration serviceRegistration = iterator.next();
			serviceRegistration.unregister();
		}
		
		unregisterSingletons();
	}


	/**
	 * Registers the InitialContextFactoryBuilder static singleton
	 * @throws NamingException on any error that occurs during the setting
	 *         of the builder.  
	 */
	private static void registerInitialContextFactoryBuilderSingleton() throws NamingException {
		try {
			NamingManager.setInitialContextFactoryBuilder(new TraditionalInitialContextFactoryBuilder());
		}
		catch (IllegalStateException illegalStateException) {
			logger.log(Level.SEVERE, 
			           "Gemini Naming Implementation cannot set the InitialContextFactoryBuilder - another builder was already installed",
			           illegalStateException);
			NamingException namingException = 
				new NamingException("Error occurred while attempting to set the IntialContextFactoryBuilder.");
			namingException.setRootCause(illegalStateException);
			throw namingException;
		} 
		catch(SecurityException securityException) {
			logger.log(Level.SEVERE, 
					   "Gemini Naming Implementation did not have the proper security permissions to install the InitialContextFactoryBuilder",
					   securityException);
			NamingException namingException = 
				new NamingException("Error occurred while attempting to set the IntialContextFactoryBuilder.");
			namingException.setRootCause(securityException);
			throw namingException;
		}
	}
	
	
	/**
	 * Registers the ObjectFactoryBuilder static singleton
	 * @throws NamingException on any error that occurs during the setting
	 *         of the builder.  
	 */
	private static void registerObjectFactoryBuilderSingleton() throws NamingException {
		try {
			NamingManager.setObjectFactoryBuilder(new TraditionalObjectFactoryBuilder());
		}
		catch (IllegalStateException illegalStateException) {
			logger.log(Level.SEVERE, 
			           "Gemini Naming Implementation cannot set the ObjectFactoryBuilder - another builder was already installed",
			           illegalStateException);
			NamingException namingException = 
				new NamingException("Error occurred while attempting to set the ObjectFactoryBuilder.");
			namingException.setRootCause(illegalStateException);
			throw namingException;
		} 
		catch(SecurityException securityException) {
			logger.log(Level.SEVERE, 
					   "Gemini Naming Implementation did not have the proper security permissions to install the ObjectFactoryBuilder",
					   securityException);
			NamingException namingException = 
				new NamingException("Error occurred while attempting to set the ObjectFactoryBuilder.");
			namingException.setRootCause(securityException);
			throw namingException;
		}
	}
		
	/**
	 * Unregisters the InitialContextFactoryBuilder static singleton
	 * and the ObjectFactoryBuilder static singleton.
	 */
	private static void unregisterSingletons() {
		Field[] fields = NamingManager.class.getDeclaredFields();
		if (fields != null && fields.length > 0) {
			for (Field field: fields) {
				if (InitialContextFactoryBuilder.class.equals(field.getType()) 
						|| ObjectFactoryBuilder.class.equals(field.getType())){
					field.setAccessible(true);
					try {
						field.set(null, null);
					} catch (IllegalArgumentException e) {
						logger.log(Level.SEVERE,
								   "Unable to reset NamingManager static singleton " + field.getType(),
								   e);
					} catch (IllegalAccessException e) {
						logger.log(Level.SEVERE,
								   "Unable to reset NamingManager static singleton " + field.getType(),
								   e);
					}
				}
			}
		}
	}
	
	
	
	
	/**
	 * Registers the OSGi URL Context Factory.
	 * 
	 */
	private void registerOSGiURLContextFactory() {
		Hashtable<Object, Object> serviceProperties = new Hashtable<Object, Object>();
		serviceProperties.put(JNDIConstants.JNDI_URLSCHEME, OSGI_URL_SCHEME);

		ServiceRegistration serviceRegistration = 
			m_bundleContext.registerService(ObjectFactory.class.getName(), 
										    new OSGiURLContextFactoryServiceFactory(), 
										    serviceProperties);
		m_listOfServiceRegistrations.add(serviceRegistration);
	}
	
	
	/**
	 * Registers the InitialContextFactoryBuilder implementation that 
	 * is responsible for loading the JDK-defined providers that must be
	 * loaded from the boot classpath.  
	 * 
	 */
	private void registerDefaultRuntimeBuilder() {
		ServiceRegistration serviceRegistration = 
			m_bundleContext.registerService(InitialContextFactoryBuilder.class.getName(), 
					                        new DefaultRuntimeInitialContextFactoryBuilder(), 
					                        null);
		m_listOfServiceRegistrations.add(serviceRegistration);
		
		Hashtable<Object, Object> props = new Hashtable<Object, Object>();
        props.put(JNDIConstants.JNDI_URLSCHEME, RMI_URL_SCHEME);
		try {
			ServiceRegistration rmiRegistration = 
				m_bundleContext.registerService(ObjectFactory.class.getName(),
												ClassLoader.getSystemClassLoader().loadClass(RMI_URL_CONTEXT_FACTORY).newInstance(),
												props);
			m_listOfServiceRegistrations.add(rmiRegistration);
		}
		catch(ClassNotFoundException e) {
			logger.log(Level.SEVERE, RMI_URL_CONTEXT_FACTORY + " cannot be found through the system classloader.", e);
		}
		catch(InstantiationException e) {
			logger.log(Level.SEVERE, "Exception occurred while instantiating " + RMI_URL_CONTEXT_FACTORY, e);
		}
		catch(IllegalAccessException e) {
			logger.log(Level.SEVERE, "Exception occured while instantiating " + RMI_URL_CONTEXT_FACTORY, e);
		}
	}
	
	
	private void registerContextManager() {
		m_contextManagerServiceFactory = 
			new ContextManagerServiceFactoryImpl(m_bundleContext);
		ServiceRegistration serviceRegistration = 
			m_bundleContext.registerService(JNDIContextManager.class.getName(),
											m_contextManagerServiceFactory,
					                        null);
		m_listOfServiceRegistrations.add(serviceRegistration);
	}
	

	private void registerProviderAdmin() {
		m_providerAdminService = 
			new SecurityAwareProviderAdminImpl(new ProviderAdminImpl(m_bundleContext));
		
		
		ServiceRegistration serviceRegistration =  
			m_bundleContext.registerService(JNDIProviderAdmin.class.getName(),
					                        m_providerAdminService,
					                        null);
		m_listOfServiceRegistrations.add(serviceRegistration);
	}

}