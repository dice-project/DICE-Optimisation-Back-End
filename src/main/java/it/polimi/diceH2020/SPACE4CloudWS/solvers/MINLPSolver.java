package it.polimi.diceH2020.SPACE4CloudWS.solvers;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;

import javax.annotation.PostConstruct;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import it.polimi.diceH2020.SPACE4CloudWS.connection.SshConnector;
import it.polimi.diceH2020.SPACE4CloudWS.core.FileUtility;

@Component
public class MINLPSolver {

	private static final String MY_FILES = "/myFiles";

	private static final String RESULTS_SOLFILE = "/results/solution.sol";

	private static final String REMOTE_SCRATCH = "/scratch";
	private static final String REMOTE_RESULTS = "/results";

	private static final String REMOTEPATH_DATA_DAT = REMOTE_SCRATCH + "/data.dat";

	private static final String REMOTEPATH_DATA_RUN = "data.run";

	private static SshConnector connector; // this can be changed with a bean

	private static Logger logger = Logger.getLogger(MINLPSolver.class.getName());

	@Autowired
	private MINLPSettings connSettings;

	@Autowired
	private Environment environment; // this is to check which is the active
										// profile at runtime


	public MINLPSolver() {
	}

	@PostConstruct
	private void init() {
		connector = new SshConnector(connSettings);
	}

	public Float run(String nameDatFile, String nameSolFile) throws Exception {
		float objFunctionValue = 0;
		String fullRemotePath = connSettings.getRemoteWorkDir() + REMOTEPATH_DATA_DAT;
		String fullLocalPath = FileUtility.LOCAL_DYNAMIC_FOLDER + File.separator + nameDatFile;
		connector.sendFile(fullLocalPath, fullRemotePath);
		logger.info("file " + nameDatFile + " has been sent");
		String nameRunFile = FileUtility.generateRunFile(connSettings.getSolverPath());

		fullRemotePath = connSettings.getRemoteWorkDir() + REMOTE_SCRATCH + "/" + REMOTEPATH_DATA_RUN;
		fullLocalPath = FileUtility.LOCAL_DYNAMIC_FOLDER + File.separator + nameRunFile;
		connector.sendFile(fullLocalPath, fullRemotePath);
		logger.info("File " + nameRunFile + " file has been sent");

		logger.info("Processing execution...");
		clearResultDir();
		String command = "cd " + connSettings.getRemoteWorkDir() + REMOTE_SCRATCH + " && "
				+ connSettings.getAmplDirectory() + " " + REMOTEPATH_DATA_RUN;
		logger.info("Remote exit status: " + connector.exec(command));

		fullLocalPath = FileUtility.createLocalSolFile(nameSolFile);

		fullRemotePath = connSettings.getRemoteWorkDir() + RESULTS_SOLFILE;

		logger.info("Solution file has been created");
		connector.receiveFile(fullLocalPath, fullRemotePath);

		objFunctionValue = this.analyzeSolution(fullLocalPath);

		logger.info("The value of the objective function is: " + objFunctionValue);

		logger.info("Cleaning result directory");
		clearResultDir();
		return objFunctionValue;
	}

	private float analyzeSolution(String solFilePath) throws IOException {
		File file = new File(solFilePath);
		if (!file.exists())
			file.createNewFile();
		String fileToString = FileUtils.readFileToString(file);
		String centralized = "centralized_obj = ";
		int startPos = fileToString.indexOf(centralized);
		int endPos = fileToString.indexOf('\n', startPos);
		float objFunctionValue = Float.parseFloat(fileToString.substring(startPos + centralized.length(), endPos));

		logger.info(fileToString);
		logger.info(objFunctionValue);
		return objFunctionValue;

	}

	public void test() throws IOException {
		String path = "/myFiles/centralized.run";
		InputStream res = getClass().getResourceAsStream(path);
		String theString = IOUtils.toString(res, Charset.defaultCharset());
		logger.info(theString);
	}

	public void initRemoteEnvironment() throws Exception {
		List<String> lstProfiles = Arrays.asList(this.environment.getActiveProfiles());
		String localPath = MY_FILES;
		logger.info("------------------------------------------------");
		logger.info("Starting math solver service initialization phase");
		logger.info("------------------------------------------------");
		if (lstProfiles.contains("test") && !connSettings.isForceClean()) {
			logger.info("Test phase: the remote work directory tree is assumed to be ok.");
		} else {
			logger.info("Clearing remote work directory tree");
			try {
				String root = connSettings.getRemoteWorkDir();
				String cleanRemoteDirectoryTree = "rm -rf " + root;
				connector.exec(cleanRemoteDirectoryTree);

				logger.info("Creating new remote work directory tree");
				String makeRemoteDirectoryTree =
						"mkdir -p " + root + "/{problems,utils,solve,scratch,results}";
				connector.exec (makeRemoteDirectoryTree);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			logger.info("Sending AMPL files");
			System.out.print("[#           ] Sending work files\r");
			sendFile(localPath + "/model.mod", connSettings.getRemoteWorkDir() + "/problems/model.mod");
			System.out.print("[##          ] Sending work files\r");
			sendFile(localPath + "/compute_psi.run", connSettings.getRemoteWorkDir() + "/utils/compute_psi.run");
			System.out.print("[###         ] Sending work files\r");
			sendFile(localPath + "/compute_job_profile.run",
					connSettings.getRemoteWorkDir() + "/utils/compute_job_profile.run");
			System.out.print("[####        ] Sending work files\r");
			sendFile(localPath + "/compute_penalties.run",
					connSettings.getRemoteWorkDir() + "/utils/compute_penalties.run");
			System.out.print("[#####       ] Sending work files\r");
			sendFile(localPath + "/save_aux.run", connSettings.getRemoteWorkDir() + "/utils/save_aux.run");
			System.out.print("[######      ] Sending work files\r");
			sendFile(localPath + "/centralized.run", connSettings.getRemoteWorkDir() + "/problems/centralized.run");
			System.out.print("[#######     ] Sending work files\r");
			sendFile(localPath + "/save_centralized.run",
					connSettings.getRemoteWorkDir() + "/utils/save_centralized.run");
			System.out.print("[########    ] Sending work files\r");
			sendFile(localPath + "/compute_s_d.run", connSettings.getRemoteWorkDir() + "/utils/compute_s_d.run");
			System.out.print("[#########   ] Sending work files\r");
			sendFile(localPath + "/AM_closed_form.run", connSettings.getRemoteWorkDir() + "/solve/AM_closed_form.run");
			System.out.print("[##########  ] Sending work files\r");
			sendFile(localPath + "/post_processing.run", connSettings.getRemoteWorkDir() + "/utils/post_processing.run");
			System.out.print("[########### ] Sending work files\r");
			sendFile(localPath + "/save_centralized.run",
					connSettings.getRemoteWorkDir() + "/utils/save_centralized.run");
			System.out.print("[############] Sending work files\r");
			logger.info("AMPL files sent");
		}
	}

	private void sendFile(String localPath, String remotePath) throws Exception {

		InputStream in = this.getClass().getResourceAsStream(localPath);
		FileOutputStream out = null;
		File tempFile = null;
		tempFile = File.createTempFile("S4C-temp", "tmp");
		tempFile.deleteOnExit();
		out = new FileOutputStream(tempFile);
		IOUtils.copy(in, out);
		connector.sendFile(tempFile.getAbsolutePath(), remotePath);
	}

	public List<String> pwd() throws Exception {
		return connector.pwd();
	}

	public String getRemoteWorkingDirectory() {
		return connSettings.getRemoteWorkDir();
	}

	public List<String> clearWorkingDir() throws Exception {
		String command = "rm -rf " + connSettings.getRemoteWorkDir();
		return connector.exec(command);
	}

	private void clearResultDir() throws Exception {
		String command = "rm -rf " + connSettings.getRemoteWorkDir() + REMOTE_RESULTS + "/*";
		connector.exec(command);
	}

	@Profile("test")
	public SshConnector getConnector() {
		return connector;
	}

}