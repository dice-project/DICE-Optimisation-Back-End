package it.polimi.diceH2020.SPACE4CloudWS.fileManagement.policy;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
@Profile("default")
public class Delete implements DeletionPolicy {
    @Override
    public boolean delete(File file) {
        return file.delete();
    }

    @Override
    public void markForDeletion(File file) {
        file.deleteOnExit();
    }
}
