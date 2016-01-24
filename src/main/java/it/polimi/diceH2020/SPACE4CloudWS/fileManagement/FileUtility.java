package it.polimi.diceH2020.SPACE4CloudWS.fileManagement;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import it.polimi.diceH2020.SPACE4CloudWS.fileManagement.policy.DeletionPolicy;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class FileUtility {

	private static final File LOCAL_DYNAMIC_FOLDER = new File("TempWorkingDir");
	private static Logger logger = Logger.getLogger(FileUtility.class.getName());

	@Autowired
	private DeletionPolicy policy;

	public boolean delete(File file) {
		return policy.delete(file);
	}

	public File provideTemporaryFile(@Nonnull String prefix, @Nullable String suffix) throws IOException {
		File file = File.createTempFile(prefix, suffix, LOCAL_DYNAMIC_FOLDER);
		policy.markForDeletion(file);
		return file;
	}

	public void writeContentToFile(@Nonnull String content, @Nonnull File file) throws IOException {
		BufferedWriter writer = new BufferedWriter(new FileWriter(file));
		writer.write(content);
		writer.close();
	}

	public void createWorkingDir() throws IOException {
		Path folder = LOCAL_DYNAMIC_FOLDER.toPath();
		Files.createDirectories(folder);
		logger.info(LOCAL_DYNAMIC_FOLDER + " created.");
	}

	public void destroyWorkingDir() throws IOException{
		Path folder = LOCAL_DYNAMIC_FOLDER.toPath();
		if (Files.deleteIfExists(folder)) {
			logger.info(LOCAL_DYNAMIC_FOLDER+ " deleted.");
		}
	}

}
