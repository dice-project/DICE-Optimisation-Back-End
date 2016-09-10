package it.polimi.diceH2020.SPACE4CloudWS.engines;

import it.polimi.diceH2020.SPACE4Cloud.shared.inputData.InstanceData;
import it.polimi.diceH2020.SPACE4Cloud.shared.settings.Scenarios;
import it.polimi.diceH2020.SPACE4Cloud.shared.settings.Settings;
import it.polimi.diceH2020.SPACE4Cloud.shared.solution.Solution;
import it.polimi.diceH2020.SPACE4CloudWS.core.BuilderSolution;
import it.polimi.diceH2020.SPACE4CloudWS.core.Matrix;
import it.polimi.diceH2020.SPACE4CloudWS.core.OptimizerFineGrained;
import it.polimi.diceH2020.SPACE4CloudWS.services.DataService;
import it.polimi.diceH2020.SPACE4CloudWS.core.BuilderMatrix;
import it.polimi.diceH2020.SPACE4CloudWS.stateMachine.Events;
import it.polimi.diceH2020.SPACE4CloudWS.stateMachine.States;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.annotation.WithStateMachine;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.Future;

@Service
@WithStateMachine
public class EngineServiceWithACService implements Engine{
	//TODO factory for this service
	
	private final Logger logger = Logger.getLogger(getClass());

	@Autowired
	@Lazy
	private OptimizerFineGrained optimizer;

	@Autowired
	private BuilderSolution solBuilder;
	
	@Autowired
	private BuilderMatrix matrixBuilder;

	@Autowired
	private DataService dataService;

	@Autowired
	private StateMachine<States, Events> stateHandler;
	
	private Solution solution;
	
	private Matrix matrix; //with admission control till now used only in private 

	public Solution getSolution() {
		return solution;
	}

	public void setSolution(Solution sol) {
		this.solution = sol;
	}

	@Async("workExecutor")
	public Future<String> runningInitSolution() {
		try {
			solution = matrixBuilder.getInitialSolution();
			matrix = matrixBuilder.getInitialMatrix(solution);
			logger.info(matrix.asString());
			if (!stateHandler.getState().getId().equals(States.IDLE)) stateHandler.sendEvent(Events.TO_CHARGED_INITSOLUTION);
		} catch (Exception e) {
			logger.error("Error while performing optimization", e);
			stateHandler.sendEvent(Events.STOP);
		}
		logger.info(stateHandler.getState().getId());
		return new AsyncResult<>("Done");
	}

	@Async("workExecutor")
	public void localSearch() {
		try {
			optimizer.hillClimbing(matrix);
		} catch (Exception e) {
			logger.error("Error while performing local search", e);
			stateHandler.sendEvent(Events.STOP);
		}
		logger.info(stateHandler.getState().getId());
	}
	
	@Async("workExecutor")
	public Future<String> reduceMatrix() {
		try {
			if(dataService.getCloudType().equals(Scenarios.PrivateAdmissionControl)){
				matrixBuilder.cellsSelectionWithKnapsack(matrix, solution);
			}else if(dataService.getCloudType().equals(Scenarios.PrivateAdmissionControlWithPhysicalAssignment)) {
				matrixBuilder.cellsSelectionWithBinPacking(matrix, solution);
			}
			optimizer.finish();
			if (!stateHandler.getState().getId().equals(States.IDLE)) stateHandler.sendEvent(Events.FINISH);
		} catch (Exception e) {
			logger.error("Error while performing optimization", e);
			stateHandler.sendEvent(Events.STOP);
		}
		logger.info(stateHandler.getState().getId());
		return new AsyncResult<>("Done");
	}

	public void changeSettings(Settings settings) {
		optimizer.changeDefaultSettings(settings);
	}

	public void restoreDefaults() {
		optimizer.restoreDefaults();
	}

	/**
	 * Set in DataService: <br>
	 * &emsp; -inputData <br>
	 * &emsp; -num job <br>
	 * &emsp; -the provider and all its available VM retrieved from DB 
	 * @param inputData
	 *            the inputData to set
	 */
	public void setInstanceData(InstanceData inputData) {
		dataService.setInstanceData(inputData);
	}

	/**
	 *  Evaluate the Solution/matrix with the specified solver (QN, SPN)
	 */
	@Async("workExecutor")
	public void evaluatingInitSolution() {
		optimizer.evaluate(matrix);
		solution.setEvaluated(false);
		
		if (!stateHandler.getState().getId().equals(States.IDLE)) stateHandler.sendEvent(Events.TO_EVALUATED_INITSOLUTION);
		logger.info(stateHandler.getState().getId());
	}
	
	
	//Used only for Tests
	public Optional<Solution> generateInitialSolution() {
		try {
			solution = solBuilder.getInitialSolution();
			return Optional.of(solution);
		} catch (Exception e) {
			logger.error("Error while performing initial solution", e);
			stateHandler.sendEvent(Events.STOP);
		}
		logger.info(stateHandler.getState().getId());
		return Optional.empty();
	}
}