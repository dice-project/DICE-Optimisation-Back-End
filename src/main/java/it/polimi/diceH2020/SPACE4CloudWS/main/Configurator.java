package it.polimi.diceH2020.SPACE4CloudWS.main;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import it.polimi.diceH2020.SPACE4Cloud.shared.InstanceData;
import it.polimi.diceH2020.SPACE4CloudWS.stateMachine.States;

@Configuration
public class Configurator {

	@Value("${pool.size:10}")
	private int poolSize;

	@Value("${queue.capacity:2}")
	private int queueCapacity;

	@Bean(name = "workExecutor")
	public TaskExecutor taskExecutor() {
		ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
		taskExecutor.setMaxPoolSize(poolSize);
		taskExecutor.setQueueCapacity(queueCapacity);
		taskExecutor.afterPropertiesSet();
		return taskExecutor;
	}
	
	@Bean
	public States state() {
		return States.IDLE;
	}

	@Bean
	public ThreadPoolTaskScheduler threadPoolTaskScheduler() {
		return new ThreadPoolTaskScheduler();
	}

	@Bean
	@Profile("dev")
	public InstanceData applData() {
		return new InstanceData();
	}

	@Bean
	@Profile("test")
	public InstanceData applDataTest() {
		int Gamma = 2400; // num cores cluster
		List<String> typeVm = Arrays.asList("T1", "T2");
		String provider = "Amazon";
		List<Integer> id_job = Arrays.asList( 10, 11 ); // numJobs = 2
		double[] think = { 0.5, 0.10 }; // check
		int[][] cM = { { 3, 4 }, { 1, 2 } };
		int[][] cR = { { 1, 2 }, { 3, 4 } };
		double[] eta = { 0.1, 0.5 };
		int[] HUp = { 10, 10 };
		int[] HLow = { 5, 5 };
		int[] NM = { 2, 2 };
		int[] NR = { 1, 1 };
		double[] Mmax = { 1.5, 2.1 };
		double[] Rmax = { 1.2, 3.2 };
		double[] Mavg = { 3.1, 0.1 };
		double[] Ravg = { 2.1, 0.2 };
		double[] D = { 0.8, 1.2 };
		double[] SH1max = { 1.1, 0.9 };
		double[] SHtypmax = { 0.5, 2.1 };
		double[] SHtypavg = { 0.7, 0.6 };
		double[] job_penalty = { 0.2, 2.1 };
		int[] R = { 22, 11 };
		return new InstanceData(Gamma, typeVm, provider, id_job, think, cM, cR, eta, HUp, HLow, NM, NR, Mmax, Rmax, Mavg,
				Ravg, D, SH1max, SHtypmax, SHtypavg, job_penalty, R);
	}

}
