/*
Copyright 2016-2017 Eugenio Gianniti
Copyright 2016 Michele Ciavotta
Copyright 2016 Jacopo Rigoli

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package it.polimi.diceH2020.SPACE4CloudWS.solvers.solversImpl.QNSolver;

import it.polimi.diceH2020.SPACE4Cloud.shared.settings.SPNModel;
import it.polimi.diceH2020.SPACE4Cloud.shared.solution.SolutionPerJob;
import it.polimi.diceH2020.SPACE4CloudWS.performanceMetrics.LittleLaw;
import it.polimi.diceH2020.SPACE4CloudWS.solvers.AbstractSolver;
import it.polimi.diceH2020.SPACE4CloudWS.solvers.settings.ConnectionSettings;
import lombok.NonNull;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class QNSolver extends AbstractSolver {

	private static final Pattern patternMap = Pattern.compile("(.*)(Map[0-9]*)(J)(.*)");
	private static final Pattern patternRS = Pattern.compile("(.*)(RS[0-9]*)(J)(.*)");
	private static final Pattern patternNMR = Pattern.compile("((nm[0-9]*)|(nr[0-9]*))");

	@Override
	protected Class<? extends ConnectionSettings> getSettingsClass() {
		return QNSettings.class;
	}

	@Override
	protected Pair<Double, Boolean> run(Pair<List<File>, List<File>> pFiles, String remoteName) throws Exception {
		File jmtFile = pFiles.getLeft().stream().filter(s -> s.getName().contains(".jsimg")).findFirst().get();

		String jmtFileName = jmtFile.getName();

		String remotePath = getRemoteSubDirectory () + File.separator + jmtFileName;

		boolean stillNotOk = true;
		for (int i = 0; stillNotOk && i < MAX_ITERATIONS; ++i) {
			logger.info(remoteName + "-> Starting Queuing Net resolution on the server");

			cleanRemoteSubDirectory ();
			sendFiles(pFiles.getLeft());
			sendFiles(pFiles.getRight());
			logger.debug(remoteName + "-> Working files sent");

			String command = connSettings.getMaxDuration() == Integer.MIN_VALUE
					? String.format("java -cp %s jmt.commandline.Jmt sim %s ", connSettings.getSolverPath(), remotePath)
					: String.format("java -cp %s jmt.commandline.Jmt sim %s -maxtime %d",
					connSettings.getSolverPath(), remotePath, connSettings.getMaxDuration());

			logger.debug(remoteName + "-> Starting JMT model...");
			List<String> remoteMsg = connector.exec(command, getClass());
			if (remoteMsg.contains("exit-status: 0")) {
				stillNotOk = false;
				logger.info(remoteName + "-> The remote optimization process completed correctly");
			} else {
				logger.debug(remoteName + "-> Remote exit status: " + remoteMsg);
			}
		}

		if (stillNotOk) {
			logger.info(remoteName + "-> Error in remote optimization");
			throw new Exception("Error in the QN server");
		} else {
			File solFile = fileUtility.provideTemporaryFile(jmtFileName + "-result", ".jsim");
			connector.receiveFile(solFile.getAbsolutePath(), remotePath + "-result" + ".jsim", getClass());
			SolutionsWrapper resultObject = SolutionsWrapper.unMarshal(solFile);
			if (fileUtility.delete(solFile)) logger.debug(solFile + " deleted");

			Double throughput = resultObject.getMeanValue();
			boolean failure = resultObject.isFailed();

			return Pair.of(throughput, failure);
		}
	}

	public Pair<List<File>, List<File>> createWorkingFiles(@NonNull SolutionPerJob solPerJob) throws IOException {
		List<File> lst = retrieveReplayerFiles (solPerJob);
		Integer nContainers = solPerJob.getNumberContainers();
		Integer concurrency = solPerJob.getNumberUsers();
		Double think = solPerJob.getJob().getThink();
		String jobID = solPerJob.getId();

		QueueingNetworkModel model = ((QNSettings) connSettings).getModel();
		int nMR = (int) solPerJob.getProfile().getProfileMap().keySet().stream().filter(s -> {
			Matcher m = patternNMR.matcher(s);
			return m.matches();
		}).count();
		if (nMR > 2) { //TODO verify
			model = QueueingNetworkModel.Q1;
			logger.debug ("QN model set to Q1");
		}

		Map<String,String> inputFilesSet = new HashMap<>();
		for (File file : lst) {
			String name = file.getName();
			Matcher mapMatcher = patternMap.matcher(name);
			Matcher rsMatcher = patternRS.matcher(name);

			String stringToBeReplaced = "";
			if (mapMatcher.find()) {
				stringToBeReplaced = mapMatcher.group(2).toUpperCase();
			} else if (rsMatcher.find()) {
				stringToBeReplaced = rsMatcher.group(2).toUpperCase();
			} else {
				logger.error ("Replayer file name does not match the required regex");
			}

			logger.debug ("Pattern to replace in jsimg: "+ stringToBeReplaced);
			inputFilesSet.put(stringToBeReplaced, getRemoteSubDirectory () + File.separator + file.getName());
		}

		Map<String,String> numMR = new HashMap<>();

		for (Entry<String, Double> entry : solPerJob.getProfile().getProfileMap().entrySet()) {
			Matcher m = patternNMR.matcher(entry.getKey());
			if (m.matches()) {
				numMR.put(entry.getKey().toUpperCase(),String.valueOf(entry.getValue().intValue()));
			}
		}

		String jsimgfileContent = new QNFileBuilder()
				.setQueueingNetworkModel(model)
				.setCores(nContainers).setConcurrency(concurrency)
				.setReplayersInputFiles(inputFilesSet)
				.setNumMR(numMR)
				.setThinkRate(1 / think).setAccuracy(connSettings.getAccuracy() / 100)
				.setSignificance(((QNSettings) connSettings).getSignificance()).build();

		File jsimgTempFile = fileUtility.provideTemporaryFile(String.format("QN-%s-class%s%s%s-",
				solPerJob.getParentID(), jobID, dataProcessor.getProviderName(),
				solPerJob.getTypeVMselected().getId()), ".jsimg");

		fileUtility.writeContentToFile(jsimgfileContent, jsimgTempFile);
		List<File> jmtModel = new ArrayList<>();
		jmtModel.add(jsimgTempFile);
		return new ImmutablePair<>(jmtModel, lst);
	}

	@Override
	public Function<Double, Double> transformationFromSolverResult (SolutionPerJob solutionPerJob,
																	SPNModel model) {
		return X -> LittleLaw.computeResponseTime (X, solutionPerJob);
	}

	@Override
	public BiConsumer<SolutionPerJob, Double> initialResultSaver (SPNModel model) {
		return (SolutionPerJob spj, Double value) -> {
			spj.setThroughput (value);
			spj.setDuration (LittleLaw.computeResponseTime(value, spj));
			spj.setError (false);
		};
	}
}
