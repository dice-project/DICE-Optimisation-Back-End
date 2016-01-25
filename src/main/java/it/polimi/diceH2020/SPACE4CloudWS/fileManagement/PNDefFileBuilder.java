package it.polimi.diceH2020.SPACE4CloudWS.fileManagement;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.List;

public class PNDefFileBuilder {
    private Integer concurrency;
    private Integer numberOfMapTasks;
    private Integer numberOfReduceTasks;

    public PNDefFileBuilder setNumberOfReduceTasks(int numberOfReduceTasks) {
        this.numberOfReduceTasks = numberOfReduceTasks;
        return this;
    }

    public PNDefFileBuilder setNumberOfMapTasks(int numberOfMapTasks) {
        this.numberOfMapTasks = numberOfMapTasks;
        return this;
    }

    public PNDefFileBuilder setConcurrency(int concurrency) {
        this.concurrency = concurrency;
        return this;
    }

    public String build() throws IOException {
        InputStream inputStream = getClass().getResourceAsStream("/GreatSPN/SingleClass.def");
        List<String> lines = new LinkedList<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String inputLine;
        while ((inputLine = reader.readLine()) != null) {
            String outputLine = inputLine.replace("@@CONCURRENCY@@", concurrency.toString())
                    .replace("@@NUM_MAP@@", numberOfMapTasks.toString())
                    .replace("@@NUM_REDUCE@@", numberOfReduceTasks.toString());
            lines.add(outputLine);
        }
        return String.join("\n", lines);
    }
}