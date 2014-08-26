/*
 * Created on Mar 2, 2005
 *
 */
package fi.csc.microarray.client;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.jms.JMSException;
import javax.swing.Icon;
import javax.swing.Timer;

import org.apache.log4j.Logger;
import org.eclipse.jetty.util.IO;

import fi.csc.microarray.client.dataimport.ImportItem;
import fi.csc.microarray.client.dataimport.ImportSession;
import fi.csc.microarray.client.dataimport.ImportUtils;
import fi.csc.microarray.client.dialog.ChipsterDialog.DetailsVisibility;
import fi.csc.microarray.client.dialog.ChipsterDialog.PluginButton;
import fi.csc.microarray.client.dialog.DialogInfo.Severity;
import fi.csc.microarray.client.operation.Operation;
import fi.csc.microarray.client.operation.Operation.DataBinding;
import fi.csc.microarray.client.operation.OperationDefinition;
import fi.csc.microarray.client.operation.OperationRecord;
import fi.csc.microarray.client.operation.ToolCategory;
import fi.csc.microarray.client.operation.ToolModule;
import fi.csc.microarray.client.selection.DataSelectionManager;
import fi.csc.microarray.client.session.UserSession;
import fi.csc.microarray.client.tasks.Task;
import fi.csc.microarray.client.tasks.Task.State;
import fi.csc.microarray.client.tasks.TaskEventListener;
import fi.csc.microarray.client.tasks.TaskException;
import fi.csc.microarray.client.tasks.TaskExecutor;
import fi.csc.microarray.client.visualisation.Visualisation.Variable;
import fi.csc.microarray.client.visualisation.VisualisationFrameManager.FrameType;
import fi.csc.microarray.client.visualisation.VisualisationMethod;
import fi.csc.microarray.client.visualisation.VisualisationMethodChangedEvent;
import fi.csc.microarray.client.workflow.WorkflowManager;
import fi.csc.microarray.config.Configuration;
import fi.csc.microarray.config.DirectoryLayout;
import fi.csc.microarray.constants.VisualConstants;
import fi.csc.microarray.databeans.ContentType;
import fi.csc.microarray.databeans.DataBean;
import fi.csc.microarray.databeans.DataBean.DataNotAvailableHandling;
import fi.csc.microarray.databeans.DataBean.Link;
import fi.csc.microarray.databeans.DataBean.Traversal;
import fi.csc.microarray.databeans.DataChangeEvent;
import fi.csc.microarray.databeans.DataChangeListener;
import fi.csc.microarray.databeans.DataFolder;
import fi.csc.microarray.databeans.DataItem;
import fi.csc.microarray.databeans.DataManager;
import fi.csc.microarray.databeans.DataManager.ValidationException;
import fi.csc.microarray.databeans.HistoryText;
import fi.csc.microarray.exception.MicroarrayException;
import fi.csc.microarray.filebroker.ChecksumException;
import fi.csc.microarray.filebroker.ChecksumInputStream;
import fi.csc.microarray.filebroker.DbSession;
import fi.csc.microarray.filebroker.DerbyMetadataServer;
import fi.csc.microarray.filebroker.QuotaExceededException;
import fi.csc.microarray.messaging.SourceMessageListener;
import fi.csc.microarray.messaging.auth.AuthenticationRequestListener;
import fi.csc.microarray.messaging.auth.ClientLoginListener;
import fi.csc.microarray.module.Module;
import fi.csc.microarray.module.ModuleManager;
import fi.csc.microarray.module.chipster.ChipsterInputTypes;
import fi.csc.microarray.module.chipster.MicroarrayModule;
import fi.csc.microarray.util.Exceptions;
import fi.csc.microarray.util.Files;
import fi.csc.microarray.util.IOUtils;


/**
 * This is the logical essence of Chipster client application. It does
 * not tell how client should look or react, but what and how it should
 * do.
 *  
 * @author Aleksi Kallio
 *
 */
public abstract class ClientApplication {

	protected static final String ALIVE_SIGNAL_FILENAME = "i_am_alive";

	protected static final int MEMORY_CHECK_INTERVAL = 2*1000;
	protected static final int SESSION_BACKUP_INTERVAL = 5 * 1000;

	// Logger for this class
	protected static Logger logger;

	public static enum SessionSavingMethod {
		LEAVE_DATA_AS_IT_IS,
		INCLUDE_DATA_INTO_ZIP,
		UPLOAD_DATA_TO_SERVER;
	}
	
    // 
	// ABSTRACT INTERFACE
	//
	public abstract void initialiseGUIThreadSafely(File mostRecentDeadTempDirectory) throws MicroarrayException, IOException;
	public abstract void reportInitialisationThreadSafely(String report, boolean newline);
	public abstract void reportExceptionThreadSafely(Exception e);
	public abstract void reportException(Exception e);
	public abstract void reportTaskError(Task job) throws MicroarrayException;		
	public abstract void showDialog(String title, String message, String details, Severity severity, boolean modal);
	public abstract void showDialog(String title, String message, String details, Severity severity, boolean modal, DetailsVisibility detailsVisibility, PluginButton button);
	public abstract void showDialog(String title, String message, String details, Severity severity, boolean modal, DetailsVisibility detailsVisibility, PluginButton button, boolean feedBackEnabled);	
	public abstract void runBlockingTask(String taskName, final Runnable runnable);

	
	/**
	 * Gets default visualisation method for selected databeans. The method is
	 * selected by following steps:
	 * 
	 * <ol>
	 * <li>If no dataset is selected, return
	 * <code>VisualisationMethod.NONE</code> </li>
	 * <li>If only one dataset is selected, return the default method for the
	 * data </li>
	 * </li>
	 * <li>If multiple datasets are selected, check the best method for each
	 * dataset. If the best method is same for all selected datasets and it can
	 * be used with multiple data, the best method is returned. </li>
	 * <li>If the best method is not same for all of the datas, try to find
	 * just some method which is suitable for all datas and can be used with
	 * multiple datasets. </li>
	 * <li>If there were no method to fill the requirements above, return
	 * <code>VisualisationMethod.NONE</code> </li>
	 * 
	 * @return default visualisation method which is suitable for all selected
	 *         datasets
	 */
	public VisualisationMethod getDefaultVisualisationForSelection() {
		logger.debug("getting default visualisation");
		if (getSelectionManager().getSelectedDataBeans() == null || getSelectionManager().getSelectedDataBeans().size() == 0) {
			return VisualisationMethod.getDefault();
		}

		try {
			List<DataBean> beans = getSelectionManager().getSelectedDataBeans();

			if (beans.size() == 1) {
				return Session.getSession().getVisualisations().getDefaultVisualisationFor(beans.get(0));
			} else if (beans.size() > 1)
				for (VisualisationMethod method : Session.getSession().getVisualisations().getOrderedDefaultCandidates()) {
					if (!method.getHeadlessVisualiser().isForMultipleDatas()) {
						continue;
					}
					if (method.isApplicableTo(beans)) {
						return method;
					}
				}

			/*
			 * 
			 * VisualisationMethod defaultMethodForDatas = null; // First, try
			 * to find best suitable visualisation for all for (DataBean bean :
			 * beans) { VisualisationMethod method = new
			 * BioBean(bean).getDefaultVisualisation(); if
			 * (defaultMethodForDatas == null &&
			 * VisualisationMethod.isApplicableForMultipleDatas(method)) {
			 * defaultMethodForDatas = method; } else { if
			 * (defaultMethodForDatas != method) { // Searching for best method
			 * for all failed defaultMethodForDatas = null; logger.debug("Method " +
			 * method + " can not be used to visualise selected datas"); break; } } }
			 * 
			 * if (defaultMethodForDatas != null) { // Visualise datas if the
			 * best method was found logger.debug("Method " +
			 * defaultMethodForDatas + " will be used to visualise selected
			 * datas"); return defaultMethodForDatas; } // Keep looking for
			 * suitable visualisation DataBean firstData = beans.get(0);
			 * 
			 * for (VisualisationMethod method :
			 * VisualisationMethod.getApplicableForMultipleDatas()) { if (method ==
			 * VisualisationMethod.NONE) { continue; }
			 * 
			 * if (method.isApplicableTo(firstData)) { // The method is
			 * applicable to one of the selected datas // Check that the same
			 * method is applicable to the other // datasets too boolean
			 * isSuitableMethod = true; for (DataBean otherData : beans) { if
			 * (otherData.equals(firstData)) { continue; }
			 * 
			 * if (!method.isApplicableTo(otherData)) { isSuitableMethod =
			 * false; logger.debug("Method " + method + " can not be used to
			 * visualise selected datas"); break; } }
			 * 
			 * if (isSuitableMethod) { logger.debug("Method " + method + " will
			 * be used to visualise selected datas"); return method; } } }
			 */
			return VisualisationMethod.getDefault();

		} catch (Exception e) {
			reportException(e);
			return VisualisationMethod.getDefault();
		}
	}
	
	protected String metadata;
	protected CountDownLatch definitionsInitialisedLatch = new CountDownLatch(1);
	
	private boolean eventsEnabled = false;
	private PropertyChangeSupport eventSupport = new PropertyChangeSupport(this);
	
	protected String requestedModule;

	/**
	 * Tool modules contain tool categories, that contain the tools. 
	 */
	protected LinkedList<ToolModule> toolModules = new LinkedList<ToolModule>(); 
	protected WorkflowManager workflowManager;
	protected DataManager manager;
    protected DataSelectionManager selectionManager;
    protected ServiceAccessor serviceAccessor;
	protected TaskExecutor taskExecutor;
	private AuthenticationRequestListener overridingARL;

	protected boolean unsavedChanges = false;
	protected boolean unbackuppedChanges = false;
	protected String currentRemoteSession = null;

	protected File aliveSignalFile;
	private LinkedList<File> deadDirectories = new LinkedList<File>();

    protected ClientConstants clientConstants;
    protected Configuration configuration;

	private String initialisationWarnings = "";
	
	private String announcementText = null;	

	public ClientApplication() {
		this(null);
	}

	public ClientApplication(AuthenticationRequestListener overridingARL) {
		this.configuration = DirectoryLayout.getInstance().getConfiguration();
		this.clientConstants = new ClientConstants();
		this.serviceAccessor = new RemoteServiceAccessor();
		this.overridingARL = overridingARL;
	}
    
	public void initialiseApplication(boolean fast) throws MicroarrayException, IOException {
		
		//Executed outside EDT, modification of Swing forbidden
		
		// these had to be delayed as they are not available before loading configuration
		logger = Logger.getLogger(ClientApplication.class);

		try {

			// Fetch announcements
			fetchAnnouncements();
						
			if (requestedModule == null) {
				requestedModule = MicroarrayModule.class.getName();
			}
			
			// Initialise modules
			final ModuleManager modules = new ModuleManager(requestedModule);
			Session.getSession().setModuleManager(modules);
			
			// Initialise workflows
			this.workflowManager = new WorkflowManager(this);

			
			// Initialise data management
			this.manager = new DataManager();
						
			Session.getSession().setDataManager(manager);		
			
			modules.plugAll(this.manager, Session.getSession());
						
			this.selectionManager = new DataSelectionManager(this);
			Session.getSession().setClientApplication(this);			
			
			// try to initialise JMS connection (or standalone services)
			logger.debug("Initialise JMS connection.");
			Session.getSession().setServiceAccessor(serviceAccessor);
			reportInitialisationThreadSafely("Connecting to broker at " + configuration.getString("messaging", "broker-host") + "...", false);
			serviceAccessor.initialise(manager, getAuthenticationRequestListener());
			
			this.taskExecutor = serviceAccessor.getTaskExecutor();
			reportInitialisationThreadSafely(" ok", true);

			if (!fast) {
				// Check services
				reportInitialisationThreadSafely("Checking remote services...", false);
				String status = serviceAccessor.checkRemoteServices();
				if (!ServiceAccessor.ALL_SERVICES_OK.equals(status)) {
					throw new Exception(status);
				}
				reportInitialisationThreadSafely(" ok", true);
			}
			
			// Fetch descriptions from compute server
			reportInitialisationThreadSafely("Fetching analysis descriptions...", false);
			initialisationWarnings += serviceAccessor.fetchDescriptions(modules.getPrimaryModule());
			toolModules.addAll(serviceAccessor.getModules());

			// Add local modules also when in remote mode
			if (!isStandalone()) {
				ServiceAccessor localServiceAccessor = new LocalServiceAccessor();
				localServiceAccessor.initialise(manager, null);
				localServiceAccessor.fetchDescriptions(modules.getPrimaryModule());
				toolModules.addAll(localServiceAccessor.getModules());
			}

			// Add internal operation definitions
			ToolCategory internalCategory = new ToolCategory("Internal tools");
			internalCategory.addOperation(OperationDefinition.IMPORT_DEFINITION);
			internalCategory.addOperation(OperationDefinition.CREATE_DEFINITION);
			ToolModule internalModule = new ToolModule("internal");
			internalModule.addHiddenToolCategory(internalCategory);
			toolModules.add(internalModule);

			// Update to splash screen that we have loaded tools
			reportInitialisationThreadSafely(" ok", true);

			// definitions are now initialised
			definitionsInitialisedLatch.countDown();
			
			File mostRecentDeadTempDirectory = null;
			
			if (!fast) {
				reportInitialisationThreadSafely("Checking session backups...", false);
				mostRecentDeadTempDirectory = checkTempDirectories();
				reportInitialisationThreadSafely(" ok", true);
			}
			// we can initialise graphical parts of the system
			initialiseGUIThreadSafely(mostRecentDeadTempDirectory);

			// Remember changes to confirm close only when necessary and to backup when necessary
			manager.addDataChangeListener(new DataChangeListener() {
				public void dataChanged(DataChangeEvent event) {
					unsavedChanges = true;
					unbackuppedChanges = true;
				}
			});
			
			// Start checking if background backup is needed
			aliveSignalFile = new File(manager.getRepository(), "i_am_alive");
			aliveSignalFile.createNewFile();
			aliveSignalFile.deleteOnExit();
			

			Timer timer = new Timer(SESSION_BACKUP_INTERVAL, new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					aliveSignalFile.setLastModified(System.currentTimeMillis()); // touch the file
					if (unbackuppedChanges) {

						File sessionFile = UserSession.findBackupFile(getDataManager().getRepository(), true);
						sessionFile.deleteOnExit();

						try {
							getDataManager().saveLightweightSession(sessionFile);

						} catch (Exception e1) {
							logger.warn(e1); // do not care that much about failing session backups
						}
					}
					unbackuppedChanges = false;
				}
			});

			timer.setCoalesce(true);
			timer.setRepeats(true);
			timer.setInitialDelay(SESSION_BACKUP_INTERVAL);
			timer.start();
			
			// disable http cache (only after initialization, because it makes 
			// icon loading from jar much slower (about 18 seconds for icons in VisualConstants) 
			IOUtils.disableHttpCache();
			
		} catch (Exception e) {
			e.printStackTrace();
			throw new MicroarrayException(e);
		}


	}	

	/**
	 * Only root folder supported in this implementation.
	 * 
	 * @param folderName subclasses may use this to group imported datasets
	 * @return always root folder in this implementation
	 */
	public DataFolder initializeFolderForImport(String folderName) {
		return manager.getRootFolder();
	}
	/**
	 * Add listener for applications state changes.
	 */
	public void addClientEventListener(PropertyChangeListener listener) {
		eventSupport.addPropertyChangeListener(listener);		
	}

	/**
	 * @see #addClientEventListener(PropertyChangeListener)
	 */
    public void removeClientEventListener(PropertyChangeListener listener) {
        eventSupport.removePropertyChangeListener(listener);       
    }
    
    public DataSelectionManager getSelectionManager() {
    	return selectionManager;
    }
    
    public void selectAllItems(){
		List<DataBean> datas = manager.databeans();
		for (DataBean data : datas) {
			
			selectionManager.selectMultiple(data, this);
			
		}
    }

	public void setVisualisationMethod(VisualisationMethod method, List<Variable> variables, List<DataBean> datas, FrameType target ) {
		fireClientEvent(new VisualisationMethodChangedEvent(this, method, variables, datas, target));
	}
	
	public void setVisualisationMethod(VisualisationMethodChangedEvent e){
		fireClientEvent(e);
	}
	
	public void setEventsEnabled(boolean eventsEnabled) {
		this.eventsEnabled = eventsEnabled;
		taskExecutor.setEventsEnabled(eventsEnabled);			
	}
	
	/**
	 * Renames the given dataset with the given name and updates the change
	 * on screen.
	 * 
	 * @param data Dataset to rename.
	 * @param newName The new name. Must contain at least one character.
	 */
	public void renameDataItem(DataItem data, String newName) {
		data.setName(newName);
	}
	

	public Task executeOperation(final Operation operation) {

		// check if guest user
		if (!operation.getDefinition().isLocal() 
				&& Session.getSession().getUsername() != null 
				&& Session.getSession().getUsername().equals(configuration.getString("security", "guest-username"))) {
			
			showDialog("Running tools is disabled for guest users.", "",
					null, Severity.INFO, true, DetailsVisibility.DETAILS_ALWAYS_HIDDEN, null);
			return null;
		}
		
		// check job count
		if (taskExecutor.getRunningTaskCount() >= clientConstants.MAX_JOBS) {
			showDialog("Task not started as there are maximum number of tasks already running.", "You can only run " + clientConstants.MAX_JOBS + " tasks at the same time. Please wait for one of the currently running tasks to finish and try again.",
						null, Severity.INFO, false);
			return null;
		}
		
		// start executing the task
		Task task = taskExecutor.createTask(operation);
		
		task.addTaskEventListener(new TaskEventListener() {
			public void onStateChange(Task job, State oldState, State newState) {
				if (newState.isFinished()) {
					try {
						// FIXME there should be no need to pass the operation as it goes within the task
						onFinishedTask(job, operation);
					} catch (Exception e) {
						reportException(e);
					}
				}
			}
		});

		try {
			onNewTask(task, operation);
			
			taskExecutor.startExecuting(task);
		} catch (TaskException | MicroarrayException | IOException te) {
			reportException(te);
		}
		
		return task;
	}
	
	public void onNewTask(Task task, Operation oper) throws MicroarrayException, IOException {
		
		Module primaryModule = Session.getSession().getPrimaryModule();
		
		for (String inputName : task.getInputNames()) {
			DataBean input = task.getInput(inputName);

			if (primaryModule.isMetadata(input)) {				
				primaryModule.preProcessInputMetadata(oper, input);				
			}
		}
	}
	
	/**
	 * When a job finishes, this is called by the JobEventListener that
	 * monitors the execution. This creates a new dataset out of the
	 * results and inserts it to the data set views.
	 * 
	 * @param task The finished task.
	 * @param oper The finished operation, which in fact is the GUI's
	 * 			   abstraction of the concrete executed job. Operation
	 * 			   has a decisively longer life span than its
	 * 			   corresponding job entity.
	 * @throws MicroarrayException 
	 * @throws IOException 
	 */
	public void onFinishedTask(Task task, Operation oper) throws MicroarrayException, IOException {
		
		LinkedList<DataBean> newBeans = new LinkedList<DataBean>();
		try {

			logger.debug("operation finished, state is " + task.getState());
			
			if (task.getState() == State.CANCELLED) {
				// task cancelled, do nothing
				
			} else if (!task.getState().finishedSuccesfully()) {
				// task unsuccessful, report it
				reportTaskError(task);
				
			} else {
				// task completed, create datasets etc.
				newBeans = new LinkedList<DataBean>();

				// read operated datas
				Module primaryModule = Session.getSession().getPrimaryModule();
				LinkedList<DataBean> sources = new LinkedList<DataBean>();
				for (DataBinding binding : oper.getBindings()) {
					// do not create derivation links for metadata datasets
					// also do not create links for sources without parents
					// this happens when creating the input databean for example
					// for import tasks
					// FIXME should such a source be deleted here?
					if (!primaryModule.isMetadata(binding.getData()) && (binding.getData().getParent() != null)) {
						sources.add(binding.getData());

					}
				}

				// decide output folder
				DataFolder folder = null;
				if (oper.getOutputFolder() != null) {
					folder = oper.getOutputFolder();
				} else if (sources.size() > 0) {
					for (DataBean source : sources) {
						if (source.getParent() != null) {
							folder = source.getParent();
						}
					}
				}
				// use root if no better option 
				if (folder == null) {
					folder = manager.getRootFolder();
				}


				// read outputs and create derivational links for non-metadata beans
				DataBean metadataOutput = null;
				OperationRecord operationRecord = new OperationRecord(oper);
				operationRecord.setSourceCode(task.getSourceCode());
				
				for (String outputName : task.outputNames()) {

					DataBean output = task.getOutput(outputName);
					output.setOperationRecord(operationRecord);


					// set sources
					for (DataBean source : sources) {
						output.addLink(Link.DERIVATION, source);
					}

					// connect data (events are generated and it becomes visible)
					manager.connectChild(output, folder);

					// check if this is metadata
					// for now this must be after folder.addChild(), as type tags are added there
					if (primaryModule.isMetadata(output)) {
						metadataOutput = output;				
					}
					
					newBeans.add(output);
				}

				// link metadata output to other outputs
				if (metadataOutput != null) {
					for (DataBean bean : newBeans) {
						if (bean != metadataOutput) {
							metadataOutput.addLink(Link.ANNOTATION, bean);
						}
					}

					primaryModule.postProcessOutputMetadata(oper, metadataOutput);				
				}

			}			
	
		} finally {
			
			// notify result listener
			if (oper.getResultListener() != null) {
				if (task.getState().finishedSuccesfully()) {
					oper.getResultListener().resultData(newBeans);
				} else {
					oper.getResultListener().noResults();
				}
			}
		}
	}
	
	public void quit() {		
		logger.debug("quitting client");
		
		try {
			serviceAccessor.close();
		} catch (Exception e) {
			// do nothing
		}
	}
	
	public void fireClientEvent(PropertyChangeEvent event) {
		logger.debug("dispatching event: " + event);
		if (eventsEnabled) {
			eventSupport.firePropertyChange(event);
		}
	}

	public interface SourceCodeListener {
		public void updateSourceCodeAt(int index, String sourceCode);
	}
	
	public void fetchSourceFor(String[] operationIDs, final SourceCodeListener listener) throws MicroarrayException {
		int i = -1;		
		for (String id : operationIDs) {
			i++;
			logger.debug("describe operation " + id);
			if (id == null) {
				listener.updateSourceCodeAt(i, null);
				continue;
			}
			
			SourceMessageListener sourceListener = null;
			try {
				sourceListener = serviceAccessor.retrieveSourceCode(id);
				String source = sourceListener.waitForResponse(60, TimeUnit.SECONDS);
				listener.updateSourceCodeAt(i, source); // source can be null
				
			} catch (Exception e) {
				throw new MicroarrayException(e);
				
			} finally {
				if (sourceListener != null) {
					sourceListener.cleanUp();
				}
			}
			
		}
	}
		
	public void importWholeDirectory(File root) {
		List<Object> onlyFiles = new LinkedList<Object>();
		
		for (File file : root.listFiles()) {				
			if (file.isFile()) { //not a folder
				onlyFiles.add(file);
			}
		}
		
		ImportSession importSession = new ImportSession(ImportSession.Source.FILE, onlyFiles, root.getName(), true);
		ImportUtils.executeImport(importSession);
	}

	/**
	 * 
	 * @param toolId
	 * @return null if operation definition is not found
	 */
	public OperationDefinition getOperationDefinition(String toolId) {
		for (ToolModule module : toolModules) {
			OperationDefinition tool = module.getOperationDefinition(toolId);
			if (tool != null) {
				return tool;
			}
		}
		return null;
	}

	/**
	 * Get OperationDefinition which best matches the given module and category names.
	 * 
	 * Module is matched before category.
	 * 
	 * @param toolId
	 * @param moduleName
	 * @param categoryName
	 * @return null if not found
	 */
	public OperationDefinition getOperationDefinitionBestMatch(String toolId, String moduleName, String categoryName) {
		
		// module match
		ToolModule preferredModule = getModule(moduleName);
		if (preferredModule != null) {
			OperationDefinition preferredTool = preferredModule.getOperationDefinition(toolId, categoryName);
			
			// module and category match
			if (preferredTool != null) {
				return preferredTool;
			} 
			
			// module match, category mismatch
			else {
				preferredTool = preferredModule.getOperationDefinition(toolId);
				if (preferredTool != null) {
					return preferredTool;
				} 
			}
		} 
		
		// module mismatch
		else {
			OperationDefinition toolWithCategoryMismatch = null;
			for (ToolModule module : toolModules) {
				// try to find tool with matching category, return if found
				OperationDefinition tool = module.getOperationDefinition(toolId, categoryName);
				if (tool != null) {
					return tool;
				}

				// try to find tool with mismatching category
				tool = module.getOperationDefinition(toolId);
				if (tool != null) {
					toolWithCategoryMismatch = tool;
				}
			}

			// matching category not found, return with mismatch, may be null
			return toolWithCategoryMismatch;
		}
		return null;
	}
	
	public void exportToFileAndWait(final DataBean data,
			final File selectedFile) {
		try {
			File newFile = selectedFile;
			int i = 1;
			while (newFile.exists()) {
				i++;
				String[] parts = Files.parseFilename(selectedFile); 
				newFile = new File(parts[0] + File.separator + parts[1] + "_" + i + "." + parts[2]);
			}

			newFile.createNewFile();		
			FileOutputStream out = new FileOutputStream(newFile);
			ChecksumInputStream in = Session.getSession().getDataManager().getContentStream(data, DataNotAvailableHandling.EXCEPTION_ON_NA);
			IO.copy(in, out);
			out.close();
			manager.setOrVerifyChecksum(data, in.verifyChecksums());
		} catch (ChecksumException e) {
			reportExceptionThreadSafely(new ChecksumException("checksum validation of the exported file failed", e));
		} catch (Exception e) {
			reportExceptionThreadSafely(e);
		}
	}

	public TaskExecutor getTaskExecutor() {
		return this.taskExecutor;
	}
	
	protected AuthenticationRequestListener getAuthenticationRequestListener() {

		AuthenticationRequestListener authenticator;

		if (overridingARL != null) {
			authenticator = overridingARL;
		} else {
			authenticator = new Authenticator();
		}

		authenticator.setLoginListener(new ClientLoginListener() {
			public void firstLogin() {
			}

			public void loginCancelled() {
				System.exit(1);
			}
		});

		return authenticator;
	}
	
	/**
	 * @return true if client is running in standalone mode (no connection to server).
	 */
	public boolean isStandalone() {
		return false; // standalone not supported
	}

	/**
	 * Collects all dead temp directories and returns the most recent
	 * that has a restorable session .
	 */
	protected File checkTempDirectories() throws IOException {

		Iterable<File> tmpDirectories = getDataManager().listAllRepositories();
		File mostRecentDeadSignalFile = null;
		
		for (File directory : tmpDirectories) {

			// Skip current temp directory
			if (directory.equals(getDataManager().getRepository())) {
				continue;
			}			
			
			// Check is it alive, wait until alive file should have been updated
			File aliveSignalFile = new File(directory, ALIVE_SIGNAL_FILENAME);
			long originalLastModified = aliveSignalFile.lastModified();
			boolean unsuitable = false;
			while ((System.currentTimeMillis() - aliveSignalFile.lastModified()) < 2*SESSION_BACKUP_INTERVAL) {			
				
				// Updated less than twice the interval time ago ("not too long ago"), so keep on checking
				// until we see new update that confirms it is alive, or have waited long
				// enough that the time since last update grows larger than twice the interval.
				
				// Check if restorable
				if (UserSession.findBackupFile(directory, false) == null) {
					// Does not have backup file, so not interesting for backup.
					// Should be removed anyway, but removing empty directories is not
					// important enough to warrant the extra waiting that follows next.
					// So we will skip this and if it was dead, it will be anyway 
					// cleaned away in the next client startup.
					
					unsuitable = true;
					break;
				}
				
				// Check if updated
				if (aliveSignalFile.lastModified() != originalLastModified) {
					unsuitable = true;
					break; // we saw an update, it is alive
				}

				// Wait for it to update
				try {
					Thread.sleep(1000); // 1 second
				} catch (InterruptedException e) {
					// ignore
				}
			}

			if (!unsuitable) {
				// It is dead, might be the one that should be recovered, check that
				deadDirectories.add(directory);
				File deadSignalFile = new File(directory, ALIVE_SIGNAL_FILENAME);
				if (UserSession.findBackupFile(directory, false) != null 
						&& (mostRecentDeadSignalFile == null 
						|| mostRecentDeadSignalFile.lastModified() < deadSignalFile.lastModified())) {

					mostRecentDeadSignalFile = deadSignalFile;

				}
			}
		}
		
		return mostRecentDeadSignalFile != null ? mostRecentDeadSignalFile.getParentFile() : null;
	}
	
	public void clearDeadTempDirectories() {
		
		// Try to clear dead temp directories
		try {
			for (File dir : deadDirectories) {
				Files.delTree(dir);
			}
		} catch (Exception e) {
			reportException(e);
		}

		// Remove them from bookkeeping in any case
		deadDirectories.clear();
	}
	
	private ToolModule getModule(String moduleName) {
		for (ToolModule toolModule : toolModules) {
			if (toolModule.getModuleName().equals(moduleName)) {
				return toolModule;
			}
		}
		return null;
	}
	
	public String getInitialisationWarnings() {
		return initialisationWarnings;
	}

	private void fetchAnnouncements() {
		new Thread(new Runnable() {

			@Override
			public void run() {
				InputStream input = null;
				try {
					
					URL url = new URL("http://chipster.csc.fi/announcements/client.txt");

					URLConnection connection = url.openConnection();
					connection.setUseCaches(false);
					connection.setConnectTimeout(10*1000);
					connection.setReadTimeout(10*1000);
					input = connection.getInputStream();
					announcementText = org.apache.commons.io.IOUtils.toString(input);
				} catch (Exception e) {
					// could fail for many reasons, not critical
				} finally {
					org.apache.commons.io.IOUtils.closeQuietly(input);
				}
			}
		}).start();
	}
	
	public String getAnnouncementText() {
		return this.announcementText;
	}
	
	public void restoreSessionAndWait(File file) {
		loadSessionAndWait(file, null, true, true, false);
	}
	
	public void loadSessionAndWait(final File sessionFile,
			final String sessionId, final boolean isDataless,
			final boolean clearDeadTempDirs,
			final boolean isExampleSession) {
		
		// check that it's a valid session file 
		if (!isDataless) {
			if (!UserSession.isValidSessionFile(sessionFile)) {
				Session.getSession().getApplication().showDialog("Could not open session file.", "The given file is not a valid session file.", "", Severity.INFO, true); 
				return;
			}
		}
			
		/* If there wasn't data or it was just cleared, there is no need to warn about
		 * saving after opening session. However, if there was datasets already, combination
		 * of them and new session can be necessary to save. This has to set after the import. 
		 */
		boolean somethingToSave = manager.databeans().size() != 0;

		try {
			if (sessionFile != null) {
				manager.loadSession(sessionFile, isDataless);
				currentRemoteSession = null;
			} else {
				manager.loadStorageSession(sessionId);
				currentRemoteSession = sessionId;
			}				

		} catch (Exception e) {
			if (isExampleSession) {
				Session.getSession().getApplication().showDialog("Opening example session failed.", "Please restart " + Session.getSession().getPrimaryModule().getDisplayName() + " to update example session links or see the details for more information.", Exceptions.getStackTrace(e), Severity.INFO, true, DetailsVisibility.DETAILS_HIDDEN, null);
			} else {
				Session.getSession().getApplication().showDialog("Opening session failed.", "Unfortunately the session could not be opened properly. Please see the details for more information.", Exceptions.getStackTrace(e), Severity.WARNING, true, DetailsVisibility.DETAILS_HIDDEN, null);
			}
			logger.error("loading session failed", e);
		}

		unsavedChanges = somethingToSave;
		
		// If this was restored session, clear dead temp directories in the end.
		// It is done inside this method to avoid building synchronization between
		// session loading and temp directory cleaning during restore. 
		if (clearDeadTempDirs) {
			clearDeadTempDirectories();
		}
	}

	
	public boolean saveSessionAndWait(boolean isRemote, File localFile, String remoteSessionName) {
		
		try {
			if (isRemote) {
				String sessionId = getDataManager().saveStorageSession(remoteSessionName);
				currentRemoteSession = sessionId;
			} else {
				getDataManager().saveSession(localFile);
				currentRemoteSession = null;
			}
			
			unsavedChanges = false;
			return true;
			
		} catch (ValidationException e) {
			Session.getSession().getApplication().showDialog(
					"Problem with saving the session", 
					"All the datasets were saved successfully, but there were troubles with saving " +
					"the session information about them. This means that there may be problems when " +
					"trying to open the saved session file later on.\n" +
					"\n" +
					"If you have important unsaved " +
					"datasets in this session, it might be a good idea to export such datasets using the " +
					"File -> Export functionality.", 
					e.getMessage(), Severity.WARNING, true, DetailsVisibility.DETAILS_HIDDEN, null);
			
			return false;
			
		} catch (QuotaExceededException e) {
			Session.getSession().getApplication().showDialog(
					"Quota exceeded", 
					"Saving session failed, because your disk space quota was exceeded.\n" +
					"\n" +
					"Please contact server maintainers to apply for more quota, remove some old sessions " +
					"to free more disk space or save the session on your computer using the " +
					"File -> Save local session functionality. ", 
					e.getMessage(), Severity.WARNING, true, DetailsVisibility.DETAILS_ALWAYS_HIDDEN, null);
			return false;

		} catch (Exception e) {
			Session.getSession().getApplication().showDialog(
					"Saving session failed", 
					"Unfortunately your session could not be saved. Please see the details for more " +
					"information.\n" +
					"\n" +
					"If you have important unsaved datasets in this session, it might be " +
					"a good idea to export such datasets using the File -> Export functionality.", 
					Exceptions.getStackTrace(e), Severity.WARNING, true, DetailsVisibility.DETAILS_HIDDEN, null);
			return false;
		}
	}
	
	public DataManager getDataManager() {
		return manager;
	}
	public LinkedList<ToolModule> getToolModules() {
		return toolModules;
	}
	
	public void importGroupAndWait(final Collection<ImportItem> datas,
			final String folderName) {
		DataBean lastGroupMember = null;

		try {

			for (ImportItem item : datas) {

				String dataSetName = item.getInputFilename();
				ContentType contentType = item.getType();
				Object dataSource = item.getInput();


				// Selects folder where data is imported to, or creates a
				// new one
				DataFolder folder = initializeFolderForImport(folderName);

				// create the DataBean
				DataBean data;
				if (dataSource instanceof File) {
					data = manager.createDataBean(dataSetName, (File) dataSource);
					
				} else if (dataSource instanceof URL) {
					data = manager.createDataBean(dataSetName, ((URL) dataSource));
					
				} else {
					throw new RuntimeException("unknown data source type: " + dataSource.getClass().getSimpleName());
				}

				// set the content type
				data.setContentType(contentType);

				// add the operation (all databeans have their own import
				// operation
				// instance, it would be nice if they would be grouped)
				Operation importOperation = new Operation(OperationDefinition.IMPORT_DEFINITION, new DataBean[] { data });
				data.setOperationRecord(new OperationRecord(importOperation));

				// data is ready now, make it visible
				manager.connectChild(data, folder);

				// Create group links only if both datas are raw type
				if (lastGroupMember != null && ChipsterInputTypes.hasRawType(lastGroupMember) && ChipsterInputTypes.hasRawType(data)) {

					DataBean targetData = data;

					// Link new data to all group linked datas of given cell
					for (DataBean sourceData : lastGroupMember.traverseLinks(new Link[] { Link.GROUPING }, Traversal.BIDIRECTIONAL)) {
						logger.debug("Created GROUPING link between " + sourceData.getName() + " and " + targetData.getName());
						createLink(sourceData, targetData, DataBean.Link.GROUPING);
					}

					// Create link to the given cell after looping to avoid
					// link duplication
					createLink(lastGroupMember, targetData, DataBean.Link.GROUPING);
				}

				lastGroupMember = data;

			}

		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	public String getHelpUrl(String page) {
		if (!page.startsWith(HelpMapping.MANUAL_ROOT)) {
			return HelpMapping.MANUAL_ROOT + page;
		}
		return page;
	}
	
	public String getHelpFor(OperationDefinition definition) {
		String url = definition.getHelpURL();
	    if (url != null && !url.isEmpty()) {
	        // Link is stored in operation definition
	        url = definition.getHelpURL();
	    } else {
	        // Mostly for microarray
	        // TODO: consider refactoring so that url is stored in definition
	        // and this "else" branch is not needed
	        url = HelpMapping.mapToHelppage(definition);
	    }
	    return url;
	}
	
	public String getHistoryText(DataBean data, boolean title, boolean name, boolean date, boolean oper, boolean code, boolean notes, boolean param) {
		return new HistoryText(data).getHistoryText(title, name, date, oper, code, notes, param);
	}
	
	public Icon getIconFor(DataItem element) {
		if (element instanceof DataFolder) {
			return VisualConstants.getIcon(VisualConstants.ICON_TYPE_FOLDER);
		} else {
			return Session.getSession().getPrimaryModule().getIconFor((DataBean) element);
		}
	}
	
	public void deleteDatasWithoutConfirming(DataItem... datas) {
		
		// check that we have something to delete
		if (datas.length == 0) {
			return; // no selection, do nothing
		}		
		
		// remove all selections
		getSelectionManager().clearAll(true, this);

		// do actual delete
		for (DataItem data : datas) {
			manager.delete(data);
		}
		
		unsavedChanges = false;
	}
	
	public void clearSessionWithoutConfirming() {
		this.deleteDatasWithoutConfirming(manager.getRootFolder());
	}	
	
	public void createLink(DataBean source, DataBean target, Link type) {
		source.addLink(type, target);
	}

	public void removeLink(DataBean source, DataBean target, Link type) {
		source.removeLink(type, target);
	}
	
	public List<DbSession> listRemoteSessions() throws JMSException {
		return Session.getSession().getServiceAccessor().getFileBrokerClient().listRemoteSessions();
	}
	
	public void removeRemoteSession(String sessionUuid) throws JMSException {
		
			if (currentRemoteSession != null && currentRemoteSession.equals(sessionUuid) && !getDataManager().databeans().isEmpty()) {
				showDialog("Remove prevented", "You were trying to remove a cloud session that is your last saved session. "
						+ "Removal of this session is prevented, because it may be the only copy of your current "
						+ "datasets. If you want to keep the datasets, please save them as a sessions first. If you want to remove "
						+ "the datasets, please delete them before removing the cloud session.", null, Severity.INFO, true);
				return;
			}

			serviceAccessor.getFileBrokerClient().removeRemoteSession(sessionUuid);		

	}
	
	public List<File> getWorkflows() {
		return workflowManager.getWorkflows();
	}
	
	public void saveWorkflow(File file) throws IOException {
		workflowManager.saveSelectedWorkflow(file);
	}
	
	public void runWorkflowAndWait(URL workflowScript) throws InterruptedException {
		final CountDownLatch latch = new CountDownLatch(1);
		workflowManager.runScript(workflowScript, new AtEndListener() {			
			@Override
			public void atEnd(boolean success) {
				latch.countDown();
			}
		});
		latch.await();
	}
	
	public void runWorkflow(URL workflowScript, boolean runForEach) {
		if (!runForEach) {
			// Run once
			workflowManager.runScript(workflowScript, null);

		} else {
			// Run for every selected data separately
			
			// Store current selection
			List<DataBean> datas = getSelectionManager().getSelectedDataBeans();

			// Select one by one and run workflow
			for (DataBean data : datas) {

				// Need synchronized latch to wait for each workflow execution
				final CountDownLatch latch = new CountDownLatch(1);
				AtEndListener atEndListener = new AtEndListener() {
					@Override
					public void atEnd(boolean success) {
						logger.debug("workflow run for each: at end");
						latch.countDown();
					}
				};

				// Run it
				getSelectionManager().selectSingle(data, this);
				logger.debug("workflow run for each: selected " + getSelectionManager().getSelectedDataBeans().size());
				workflowManager.runScript(workflowScript, atEndListener);
				try {
					latch.await();
				} catch (InterruptedException e) {
					// Ignore
				}
			}

			// Restore original selection
			logger.debug("workflow run for each: restore original selection");
			Collection<DataItem> items = new LinkedList<DataItem>();
			items.addAll(datas);
			getSelectionManager().selectMultiple(items, this);
		}
	}
	public boolean hasUnsavedChanges() {
		return unsavedChanges;
	}
	
	public boolean areCloudSessionsEnabled() {
		boolean conf =  DirectoryLayout.getInstance().getConfiguration().getBoolean("client", "enable-cloud-sessions");
		boolean specialUser = DerbyMetadataServer.DEFAULT_EXAMPLE_SESSION_OWNER.equals(Session.getSession().getUsername());
		
		return conf || specialUser;
	}
}
