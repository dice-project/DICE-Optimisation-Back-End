package it.polimi.diceH2020.SPACE4CloudWS.FineGrainedLogicForOptimization;

import javax.annotation.PostConstruct;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import it.polimi.diceH2020.SPACE4Cloud.shared.solution.SolutionPerJob;
import it.polimi.diceH2020.SPACE4CloudWS.services.SolverProxy;
import reactor.bus.Event;
import reactor.bus.EventBus;
import reactor.fn.Consumer;
import static reactor.bus.selector.Selectors.$;

import java.math.BigDecimal;
import java.util.Optional;

@Component
@Scope("prototype")
public class ReactorConsumer implements Consumer<Event<SpjWrapperGivenHandN>>{

	private final Logger logger = Logger.getLogger(ReactorConsumer.class.getName());
	
	@Autowired
	private EventBus eventBus;
	
	@Autowired
	private WrapperDispatcher dispatcher;
	
	@Autowired
	private SolverProxy solverCache;
	
	private int id;
	
	public ReactorConsumer(int id) {
		this.id = id;
	}
	
	public ReactorConsumer() {
	}
	
	@PostConstruct
	private void register(){
	    logger.info("|Q-STATUS| created consumer listening for event message 'channel"+id+"'");
		eventBus.on($("channel"+id), this); 
	}
	
	@Override
	public void accept(Event<SpjWrapperGivenHandN> ev) {
		SolutionPerJob spj = ev.getData().getSpj();
		SpjOptimizerGivenH spjOptimizer = ev.getData().getHandler();
		logger.info("|Q-STATUS| received spjWrapper"+spj.getJob().getId()+"."+spj.getNumberUsers()+" on channel"+id+"\n");
		if(calculateDuration(spj)){ 
			spjOptimizer.registerCorrectSolutionPerJob(spj);
		}else{
			spjOptimizer.registerFailedSolutionPerJob(spj);
		}
		dispatcher.notifyReadyChannel(this); 
	}
	
	
	private boolean calculateDuration(SolutionPerJob solPerJob) {
		Optional<BigDecimal> duration = solverCache.evaluate(solPerJob);
		if (duration.isPresent()) {
				solPerJob.setDuration(duration.get().doubleValue());
				evaluateFeasibility(solPerJob);
				return true;
		}
		solverCache.invalidate(solPerJob);
		return false;
	}
	
	private boolean evaluateFeasibility(SolutionPerJob solPerJob) {
		if (solPerJob.getDuration() <= solPerJob.getJob().getD()) {
			solPerJob.setFeasible(true);
			return true;
		}
		solPerJob.setFeasible(false);
		return false;
	}
	
	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}
}


