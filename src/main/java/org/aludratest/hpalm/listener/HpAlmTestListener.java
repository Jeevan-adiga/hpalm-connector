/*
 * Copyright (C) 2015 Hamburg Sud and the contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.aludratest.hpalm.listener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import org.aludratest.hpalm.TestCaseIdResolver;
import org.aludratest.hpalm.entity.AbstractEntityBuilder;
import org.aludratest.hpalm.entity.Entity;
import org.aludratest.hpalm.entity.RunStepStatus;
import org.aludratest.hpalm.entity.TestInstanceBuilder;
import org.aludratest.hpalm.entity.TestRunBuilder;
import org.aludratest.hpalm.impl.HpAlmConfiguration;
import org.aludratest.hpalm.infrastructure.EntityCollection;
import org.aludratest.hpalm.infrastructure.HpAlmException;
import org.aludratest.hpalm.infrastructure.HpAlmSession;
import org.aludratest.hpalm.infrastructure.HpAlmUtil;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ISuite;
import org.testng.ITestListener;
import org.testng.ITestResult;

import auto.framework.AbstractRunnerListener;

@Component(role = ITestListener.class, hint = "hpalm")
public class HpAlmTestListener extends AbstractRunnerListener {

	private static final Logger LOG = LoggerFactory.getLogger(HpAlmTestListener.class);

	private static final String[] testStepColumns = new String[] { "Started", "Command", "Element Type", "Element Name", "Data",
			"Error Message", "Technical Locator", "Technical Arguments", "Comment" };

	@Requirement
	private TestCaseIdResolver idResolver;

	@Requirement
	private HpAlmConfiguration configuration;

	private HpAlmWorkerThread workerThread;

	private TestCaseIdResolver getIdResolver() {
		return idResolver;
	}

	@Override
	/**
	 * on suite start
	 */
	public void onStart(ISuite suit) {
		System.out.println("hpalm-connector - suite start");
		if (configuration.isEnabled()) {
			if (getIdResolver() == null) {
				LOG.error("No IdResolver registered for HP ALM connector. Connector is disabled now.");
			}
			else {
				workerThread = new HpAlmWorkerThread(configuration);
				workerThread.start();
			}
		}
	}

	@Override
	public void onTestStart(ITestResult result) {
		System.out.println("hpalm-connector - test start");
		if (!configuration.isEnabled() || getIdResolver() == null) {
			return;
		}
		Long id = getIdResolver().getHpAlmTestId(result);
		if (id == null) {
			return;
		}

		Long configId = getIdResolver().getHpAlmTestConfigId(result);

		TestCaseData data = new TestCaseData();
		TestRunBuilder builder = new TestRunBuilder();
		builder.setTestId(id.longValue());
		builder.setStatus(RunStepStatus.PASSED.displayName());

		data.startTime = new DateTime();
		data.testRunBuilder = builder;
		data.hpAlmId = id.longValue();
		data.hpAlmConfigId = configId;
//		runnerLeaf.setAttribute("hpalmData", data);
	}

	@Override
	public void onTestSuccess(ITestResult result) {
		System.out.println("hpalm-connector - test success");

//		TestCaseData data = (TestCaseData) runnerLeaf.getAttribute("hpalmData");
		TestCaseData data = null;
		if (data == null || !workerThread.isAlive()) {
			return;
		}

		DateTime endTime = new DateTime();
		// @formatter:off
		data.testRunBuilder
			.setExecutionDateAndTime(data.startTime.toDate())
			.setDuration((endTime.getMillis() - data.startTime.getMillis()) / 1000)
			.setName("nameOfExecution");
		// @formatter:on

		workerThread.addTestRun(data);
	}

	@Override
	/**
	 * on suite finish
	 */
	public void onFinish(ISuite suit) {
		if (workerThread != null) {
			workerThread.terminate();

			// wait for worker thread to finish
			try {
				workerThread.join();
			}
			catch (InterruptedException e) {
				return;
			}
		}
	}

	private static class TestCaseData {

		private TestRunBuilder testRunBuilder;

		private DateTime startTime;

		private long hpAlmId;

		private Long hpAlmConfigId;

		boolean alreadyFailed = false;

	}

	private static class HpAlmWorkerThread extends Thread {

		private boolean terminated;

		private volatile boolean timeZoneInitialized;

		private HpAlmConfiguration configuration;

		private List<TestCaseData> buffer = new LinkedList<TestCaseData>();

		public HpAlmWorkerThread(HpAlmConfiguration configuration) {
			this.configuration = configuration;
		}

		private synchronized void terminate() {
			terminated = true;
			notify();
		}

		private synchronized boolean isTerminated() {
			return terminated;
		}

		private synchronized void addTestRun(TestCaseData data) {
			buffer.add(data);
			notify();
		}

		private synchronized boolean isBufferEmpty() {
			return buffer.isEmpty();
		}

		public synchronized boolean isTimeZoneInitialized() {
			return timeZoneInitialized;
		}

		@Override
		public void run() {
			// ensure configuration is valid
			try {
				configuration.getHpAlmUrl();
				configuration.getDomain();
				configuration.getProject();
				configuration.getUserName();
				configuration.getPassword();
				configuration.getTestSetFolderPath();
				configuration.getTestSetName();
			}
			catch (Exception ce) {
				LOG.error("HP ALM Connector configuration is invalid", ce);
				return;
			}

			HpAlmSession session;
			try {
				session = HpAlmSession.create(configuration.getHpAlmUrl(), configuration.getDomain(),
						configuration.getProject(), configuration.getUserName(), configuration.getPassword(),
						60000, 60000);
			}
			catch (IOException e) {
				LOG.error("Could not connect to HP ALM", e);
				return;
			}
			catch (HpAlmException e) {
				LOG.error("Could not connect to HP ALM", e);
				return;
			}

			// determine server time zone
			try {
				AbstractEntityBuilder.setTimeZone(session.determineServerTimeZone());
				synchronized (this) {
					timeZoneInitialized = true;
					notifyAll();
				}
			}
			catch (HpAlmException e) {
				LOG.warn("Could not determine server time zone", e);
			}
			catch (IOException e) {
				LOG.warn("Could not determine server time zone", e);
			}

			// ensure that configured path exists
			Entity testSetFolder;
			try {
				testSetFolder = HpAlmUtil.createTestSetFolderPath(session, configuration.getTestSetFolderPath());
			}
			catch (IOException e) {
				LOG.error("Could not create or retrieve configured test set folder " + configuration.getTestSetFolderPath(), e);
				return;
			}
			catch (HpAlmException e) {
				LOG.error("Could not create or retrieve configured test set folder " + configuration.getTestSetFolderPath(), e);
				return;
			}

			// create or get test set in folder
			Entity testSet;
			try {
				testSet = HpAlmUtil.createOrGetTestSet(session, testSetFolder.getId(),
						configuration.getTestSetName());
			}
			catch (IOException e) {
				LOG.error("Could not create or retrieve configured test set " + configuration.getTestSetName(), e);
				return;
			}
			catch (HpAlmException e) {
				LOG.error("Could not create or retrieve configured test set " + configuration.getTestSetName(), e);
				return;
			}
			long testSetId = testSet.getId();

			while (!isTerminated()) {
				// wait for buffer to receive an element
				while (!isTerminated() && isBufferEmpty()) {
					try {
						// wait up to one minute
						synchronized (this) {
							wait(60000);
						}
					}
					catch (InterruptedException e) {
						return;
					}
					// send keep-alive
					try {
						session.extendTimeout();
					}
					catch (Exception e) {
						LOG.warn("Lost connection to HP ALM; trying to reconnect", e);
						try {
							session.logout();
						}
						catch (Exception ee) { // NOPMD
							// ignore
						}

						try {
							session = HpAlmSession.create(configuration.getHpAlmUrl(), configuration.getDomain(),
									configuration.getProject(), configuration.getUserName(), configuration.getPassword());
						}
						catch (IOException ee) {
							LOG.error("Could not connect to HP ALM", ee);
							return;
						}
						catch (HpAlmException ee) {
							LOG.error("Could not connect to HP ALM", ee);
							return;
						}
					}
				}

				while (!isBufferEmpty()) {
					TestCaseData data;
					synchronized (this) {
						data = buffer.remove(0);
					}

					try {
						writeTestRun(session, data, testSetId, data.hpAlmId, data.hpAlmConfigId);
					}
					catch (IOException e) {
						LOG.error("Could not write test case "
								+ (data != null ? (data.hpAlmId + "/" + data.hpAlmConfigId) : "(unknown)")
								+ " to HP ALM", e);
					}
					catch (HpAlmException e) {
						LOG.error("Could not write test case "
								+ (data != null ? (data.hpAlmId + "/" + data.hpAlmConfigId) : "(unknown)") + " to HP ALM", e);
					}
				}
			}

			try {
				session.logout();
			}
			catch (Exception e) {
				LOG.warn("Exception when logging out from HP ALM", e);
			}
		}

		private void writeTestRun(HpAlmSession session, TestCaseData data, long testSetId, long testId, Long testConfigId)
				throws IOException,
				HpAlmException {
			// create or get the Test Instance
			Entity testInstance = HpAlmUtil.createOrGetTestInstance(session, testSetId, testId, testConfigId);

			if (testInstance == null) {
				LOG.error("Test instance for testId / testConfigId " + testId + "/" + testConfigId + " could not be created.");
				return;
			}

			// create the Test Run
			Entity testRun = data.testRunBuilder.setTestSetId(testSetId).setTestInstanceId(testInstance.getId())
					.setOwner(configuration.getUserName()).create();
			testRun = session.createEntity(testRun);

			// delete auto-created steps (from test plan)
			// first, collect them because of HP ALM pagination (otherwise, second page would be empty)
			List<Entity> toDelete = new ArrayList<Entity>();
			EntityCollection ec = session.queryEntities("run-step", "parent-id[" + testRun.getId() + "]");
			for (Entity e : ec) {
				if (e.getId() > 0) {
					toDelete.add(e);
				}
			}

			for (Entity e : toDelete) {
				session.deleteEntity(e);
			}

			long testRunId = testRun.getId();

			// update Test Instance status
			Entity testInstanceUpdate = new TestInstanceBuilder().setStatus(testRun.getStringFieldValue("status"))
					.setExecDateTimeFromEntity(testRun).create();
			session.updateEntity(testInstance.getId(), testInstanceUpdate);

			// delete auto-generated Fast_Run
			ec = session.queryEntities("run", "id[>" + testRunId + "]; test-id[" + data.hpAlmId + "]; cycle-id[" + testSetId
					+ "]; name['Fast_Run_*']");
			if (ec.getTotalCount() > 0) {
				for (Entity e : ec) {
					session.deleteEntity(e);
				}
			}
		}

	}

}
