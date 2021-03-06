package rekkura.ggp.player;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import rekkura.ggp.milleu.Game;
import rekkura.ggp.milleu.Player.ProverBased;
import rekkura.logic.model.Dob;
import rekkura.state.algorithm.DepthCharger;
import rekkura.util.Colut;
import rekkura.util.RankedCarry;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset;

/**
 * This basic Monte Carlo player will expand and represent 
 * only the actions belonging to its role in any given turn
 * and will estimate the value of those actions with uniformly
 * random depth charges.
 * @author ptpham
 *
 */
public class MonteCarloPlayer extends ProverBased {
	@Override protected void plan() { explore(); }
	@Override protected void move() { explore(); }
	@Override protected void reflect() { }
	
	private Random rand = new Random();
	private AtomicInteger wavesComputed = new AtomicInteger();
	
	private void explore() {
		setDecision(anyDecision());
		
		Game.Turn current = getTurn();
		Set<Dob> state = current.state;
		
		ListMultimap<Dob, Dob> actions = machine.getActions(state);
		List<Dob> playerActions = actions.get(role);
		
		// This holds an accumulation of goal values over
		// many depth charges.
		Multiset<Dob> goals = HashMultiset.create();
		
		RankedCarry<Integer, Dob> best = RankedCarry.createReverseNatural(-Integer.MIN_VALUE, null);
		while (validState()) {
			if (!computeWave(playerActions, goals)) break;
			
			// See if we need to update the move we want to make
			for (Dob action : goals.elementSet()) {
				int value = goals.count(action);
				if (best.consider(value, action)) setDecision(current.turn, best.carry);
			}
			wavesComputed.addAndGet(1);
		}
	}
	
	/**
	 * This method attempts to perform a single charge per action.
	 * It will bail if time has run out.
	 * 
	 * @param actions the set of moves we want to consider from the current state
	 * @param goals running sum of goal values
	 * @return returns true if all actions were considered and false otherwise.
	 */
	private boolean computeWave(Collection<Dob> actions, Multiset<Dob> goals) {
		Set<Dob> state = this.getTurn().state;
		
		for (Dob action : actions) {
			if (!validState()) return false;
			Map<Dob, Dob> fixed = Maps.newHashMap();
			fixed.put(role, action);
			
			List<Set<Dob>> charge = DepthCharger.fire(state, machine, fixed, rand);

			Set<Dob> terminal = Colut.end(charge);
			int goal = Colut.get(machine.getGoals(terminal), role, 0);
			goals.add(action, goal);
		}
		
		return true;
	}
	
	public int getWavesComputed() { return this.wavesComputed.get(); }
}