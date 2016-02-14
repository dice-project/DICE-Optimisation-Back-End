package it.polimi.diceH2020.SPACE4CloudWS.solvers.solversImp.QNSolver;

import it.polimi.diceH2020.SPACE4Cloud.shared.solution.SolutionPerJob;
import it.polimi.diceH2020.SPACE4CloudWS.solvers.AbstractSolver;
import it.polimi.diceH2020.SPACE4CloudWS.solvers.solversImp.QNSolver.generated.Solutions;
import lombok.NonNull;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Created by ciavotta on 11/02/16.
 */
@Component
public class QNSolver extends AbstractSolver {


    @Autowired
    public QNSolver(QNSettings settings) {
        this.connSettings = settings;
    }

    private BigDecimal run(List<File> pFiles, String remoteName, Integer iteration) throws Exception {
        if (iteration < MAX_ITERATIONS) {
            File jmtFile = pFiles.get(2); // it is the third in the list
            //    File resultFile = pFiles.getRight();
            String jmtFileName = jmtFile.getName();

            String remotePath = connSettings.getRemoteWorkDir() + "/" + jmtFileName;
            logger.info(remoteName + "-> Starting Queuing Net resolution on the server");

            sendFiles(pFiles);
            logger.debug(remoteName + "-> Working files sent");

            String command;
            if (((QNSettings) connSettings).getMaxTime() == Integer.MIN_VALUE)
                command = String.format("java -cp %s jmt.commandline.Jmt sim %s ", connSettings.getSolverPath(), remotePath);
            else
                command = String.format("java -cp %s jmt.commandline.Jmt sim %s -maxtime %d", connSettings.getSolverPath(), remotePath, ((QNSettings) connSettings).getMaxTime());

            logger.debug(remoteName + "-> Starting JMT model...");
            List<String> remoteMsg = connector.exec(command);
            if (remoteMsg.contains("exit-status: 0"))
                logger.info(remoteName + "-> The remote optimization proces completed correctly");
            else {
                logger.debug(remoteName + "-> Remote exit status: " + remoteMsg);
                iteration = iteration + 1;
                return run(pFiles, remoteName, iteration);
            }

            File solFile = fileUtility.provideTemporaryFile(jmtFileName + "-result", ".jsim");
            connector.receiveFile(solFile.getAbsolutePath(), remotePath + "-result" + ".jsim");
            JAXBContext jaxbContext = JAXBContext.newInstance(Solutions.class);

            Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
            Solutions resultObject = (Solutions) jaxbUnmarshaller.unmarshal(solFile);
            if (fileUtility.delete(solFile))
                logger.debug(solFile + " deleted");

            Double throughput = resultObject.getMeasure().get(0).getMeanValue();

            BigDecimal result = BigDecimal.valueOf(throughput);
            result.setScale(2, RoundingMode.HALF_EVEN);
            return result;
        } else {
            logger.debug(remoteName + "-> Error in remote optimziation");
            throw new Exception("Error in the QN server");

        }


    }


    @Override
    protected BigDecimal run(List<File> pFiles, String s) throws Exception {
        return run(pFiles, s, 0);
    }

    private List<File> createProfileFiles(@NonNull SolutionPerJob solutionPerJob) throws IOException {
        Integer jobID = solutionPerJob.getJob().getId();

        InputStream inputStreamMap = getClass().getResourceAsStream(String.format("/QN/MapJ%d.txt", jobID));
        File tempFileMap = fileUtility.provideTemporaryFile(String.format("MapJ%d", jobID), ".txt");
        FileOutputStream outputStreamTempMap = new FileOutputStream(tempFileMap);
        IOUtils.copy(inputStreamMap, outputStreamTempMap);

        InputStream inputStreamRS = getClass().getResourceAsStream(String.format("/QN/RSJ%d.txt", jobID));
        File tempFileRS = fileUtility.provideTemporaryFile(String.format("RSJ%d", jobID), ".txt");
        FileOutputStream outputStreamTempRS = new FileOutputStream(tempFileRS);
        IOUtils.copy(inputStreamRS, outputStreamTempRS);
        List<File> lst = new ArrayList<>(2);

        lst.add(tempFileMap);
        lst.add(tempFileRS);
        return lst;
    }

    private void sendFiles(List<File> lstFiles) {
        lstFiles.stream().forEach((File file) -> {
            try {
                connector.sendFile(file.getAbsolutePath(), connSettings.getRemoteWorkDir() + "/" + file.getName());
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }


    public List<File> createWorkingFiles(@NonNull SolutionPerJob solPerJob) throws IOException {
        return createWorkingFiles(solPerJob, Optional.empty());
    }

    private List<File> createWorkingFiles(SolutionPerJob solPerJob, Optional<Object> iteration) throws IOException {
        List<File> lst = createProfileFiles(solPerJob);
        Integer nContainers = solPerJob.getNumberContainers();
        Integer concurrency = solPerJob.getJob().getHup(); //TODO this must be changed
        Integer numMap = solPerJob.getProfile().getNM();
        Integer numReduce = solPerJob.getProfile().getNR();
        Double think = solPerJob.getJob().getThink();
        Integer jobID = solPerJob.getJob().getId();
        String mapFileName = lst.get(0).getName();
        String rsFileName = lst.get(1).getName();
        String remoteMapFilePath = String.format("%s/%s.txt", connSettings.getRemoteWorkDir(), mapFileName);
        String remoteRSFilePath = String.format("%s/%s.txt", connSettings.getRemoteWorkDir(), rsFileName);
        String jsimgfileContent = new QNFileBuilder().setCores(nContainers)
                .setConcurrency(concurrency)
                .setNumberOfMapTasks(numMap)
                .setNumberOfReduceTasks(numReduce)
                .setMapFilePath(remoteMapFilePath)
                .setRsFilePath(remoteRSFilePath)
                .setThinkRate(1 / think)
                .build();

        File jsimgTempFile;
        if (iteration.isPresent())
            jsimgTempFile = fileUtility.provideTemporaryFile(String.format("MR-multiuser%d-iter%d-", jobID, iteration), ".jsimg");
        else jsimgTempFile = fileUtility.provideTemporaryFile(String.format("MR-multiuser%d", jobID), ".jsimg");

        fileUtility.writeContentToFile(jsimgfileContent, jsimgTempFile);
        lst.add(jsimgTempFile);
        return lst;
    }


}