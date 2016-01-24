package it.polimi.diceH2020.SPACE4CloudWS.fileManagement.policy;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
@Profile("dev")
public class KeepFiles implements DeletionPolicy {
    @Override
    public boolean delete(File file) {
        return false;
    }

    @Override
    public void markForDeletion(File file) {
        // This is a no-op for choice
    }
}
