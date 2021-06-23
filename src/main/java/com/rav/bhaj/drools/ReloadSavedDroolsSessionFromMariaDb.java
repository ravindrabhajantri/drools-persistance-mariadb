package com.rav.bhaj.drools;

import java.util.Collection;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.persistence.Persistence;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import javax.transaction.UserTransaction;

import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.KieModule;
import org.kie.api.builder.KieRepository;
import org.kie.api.runtime.Environment;
import org.kie.api.runtime.EnvironmentName;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.internal.io.ResourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bitronix.tm.TransactionManagerServices;
import bitronix.tm.resource.jdbc.PoolingDataSource;

public class ReloadSavedDroolsSessionFromMariaDb {
	public static final String CORRELATION_DROOLS_TRANSACTIONS = "jdbc/BitronixJTADataSource";
	public static final String DRL_LOCATION = "rules/Grading.drl";
	private static final Logger LOGGER = LoggerFactory.getLogger(ReloadSavedDroolsSessionFromMariaDb.class);

	public static void main(String[] args) throws InterruptedException {
		initializeDataSource();
		KieServices kieServices = KieServices.Factory.get();
		KieSession kieSession = kieServices.getStoreServices().loadKieSession(652L, getKieBase(kieServices), null,
				getKieEnvironment(kieServices));
		UserTransaction correlationTransactions;
		try {
			correlationTransactions = (UserTransaction) new InitialContext().lookup("java:comp/UserTransaction");
			correlationTransactions.begin();

			LOGGER.info("Firing Rules on saved facts");
			kieSession.fireAllRules();

			LOGGER.info("Total inserts at KieSession: {}", kieSession.getFactCount());

			Collection<FactHandle> factHandles = kieSession.getFactHandles();
			for (FactHandle handles : factHandles) {
				LOGGER.info(handles.toString());
			}

			correlationTransactions.commit();
			kieSession.destroy();
		} catch (NamingException e) {
			e.printStackTrace();
		} catch (NotSupportedException e) {
			e.printStackTrace();
		} catch (SystemException e) {
			e.printStackTrace();
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (IllegalStateException e) {
			e.printStackTrace();
		} catch (RollbackException e) {
			e.printStackTrace();
		} catch (HeuristicMixedException e) {
			e.printStackTrace();
		} catch (HeuristicRollbackException e) {
			e.printStackTrace();
		}
	}

	private static void initializeDataSource() {
		PoolingDataSource ds = new PoolingDataSource();
		ds.setUniqueName(CORRELATION_DROOLS_TRANSACTIONS);
		ds.setClassName("org.mariadb.jdbc.MariaDbDataSource");
		ds.setMaxPoolSize(3);
		ds.setAllowLocalTransactions(true);
		ds.getDriverProperties().put("user", "root");
		ds.getDriverProperties().put("password", "root");
		ds.getDriverProperties().put("url", "jdbc:mariadb://localhost:3306/DROOLS?createDatabaseIfNotExist=true");
		ds.init();

	}

	private static Environment getKieEnvironment(KieServices kieServices) {
		Environment env = kieServices.newEnvironment();
		env.set(EnvironmentName.ENTITY_MANAGER_FACTORY,
				Persistence.createEntityManagerFactory("org.drools.persistence.jpa"));
		env.set(EnvironmentName.TRANSACTION_MANAGER, TransactionManagerServices.getTransactionManager());
		return env;
	}

	private static KieBase getKieBase(KieServices kieServices) {
		KieFileSystem kieFileSystem = kieServices.newKieFileSystem();
		kieFileSystem.write(ResourceFactory.newClassPathResource(DRL_LOCATION));
		final KieRepository kieRepository = kieServices.getRepository();

		kieRepository.addKieModule(kieRepository::getDefaultReleaseId);
		KieBuilder kb = kieServices.newKieBuilder(kieFileSystem);
		kb.buildAll();
		KieModule kieModule = kb.getKieModule();
		KieContainer kieContainer = kieServices.newKieContainer(kieModule.getReleaseId());
		return kieContainer.getKieBase();
	}

}
