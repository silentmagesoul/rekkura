
package rekkura.logic.algorithm;

import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import rekkura.logic.model.Atom;
import rekkura.logic.model.Dob;
import rekkura.logic.model.Rule;
import rekkura.logic.model.Unification;
import rekkura.logic.structure.Pool;
import rekkura.util.Cartesian;
import rekkura.util.Colut;
import rekkura.util.Limiter;
import rekkura.util.OtmUtil;

import com.google.common.collect.*;

/**
 * A renderer is responsible for generating all possible implicated dobs
 * from a single rule and the current state of the world.
 * @author ptpham
 *
 */
public abstract class Renderer {
	
	/**
	 * This method exposes an efficient rendering process for a collection of ground dobs.
	 * If you want to apply a single assignment in a vacuum, consider Terra.applyBodies.
	 * To generate the support for this function, consider using Terra.getBodySpace.
	 * @param rule
	 * @param support
	 * @param pool
	 * @param truths
	 * @return
	 */
	public abstract List<Map<Dob,Dob>> apply(Rule rule, Set<Dob> truths, Multimap<Atom,Dob> support, Pool pool);
	
	public final Limiter.Operations ops = Limiter.forOperations();
	public static Standard newStandard() { return new Standard(); }
	public static Chaining newChaining() { return new Chaining(); }
	public static Failover newStandardFailover() {
		Standard standard = newStandard();
		standard.ops.max = 1024;
		return new Failover(standard, newChaining());
	}
	
	public static class Standard extends Renderer {
		@Override public List<Map<Dob,Dob>> apply(Rule rule,
			Set<Dob> truths, Multimap<Atom, Dob> support, Pool pool) {
			Map<Atom,Integer> sizes = OtmUtil.getNumValues(support);
			ops.begin();

			List<Atom> expanders = Terra.getGreedyExpanders(rule, sizes);
			List<Atom> check = Colut.remove(rule.body, expanders);
			Cartesian.AdvancingIterator<Unification> iterator =
				Terra.getUnificationIterator(rule, expanders, support, truths);
			
			return Terra.expandUnifications(rule, check, iterator, pool, truths, this.ops);
		}
	}

	public static class Chaining extends Renderer {

		@Override
		public List<Map<Dob, Dob>> apply(Rule rule,
			Set<Dob> truths, Multimap<Atom, Dob> support, Pool pool) {
			
			ops.begin();
			List<Map<Dob,Dob>> result = Lists.newArrayList();
			if (Terra.applyVarless(rule, truths, result)) return result;
			List<Atom> positives = Atom.filterPositives(rule.body);
			List<Atom> expanders = Terra.getChainingCover(positives, rule.vars);
			List<Atom> check = Colut.remove(rule.body, expanders);

			if (expanders == null) return result;
			List<List<Unification>> space = Terra.getUnificationSpace(rule, support, expanders);
			if (Cartesian.size(space) == 0) return result;

			List<ListMultimap<Unification,Unification>> guide = Unification.getChainingGuide(space, rule.vars);
			return chain(rule, truths, check, guide, pool);
		}

		protected List<Map<Dob, Dob>> chain(Rule rule, Set<Dob> truths,
			List<Atom> check, List<ListMultimap<Unification, Unification>> guide, Pool pool) {
			
			List<Map<Dob, Dob>> result = Lists.newArrayList();
			List<Unification.Distinct> distincts = Unification.convert(rule.distinct, rule.vars);
			List<Unification> masks = Lists.newArrayList();
			for (int i = 0; i < guide.size(); i++) masks.add(Colut.any(guide.get(i).keySet()));
			
			Deque<State> frames = Queues.newArrayDeque();
			frames.push(new State(Colut.first(guide).values().iterator(), rule.vars));
			
			while (frames.size() > 0 && !ops.exceeded()) {
				State top = frames.peek();
				if (!top.remain.hasNext()) {
					frames.pop();
					continue;
				}
				
				// Apply the new partial assignment
				Colut.transferNonNull(top.unify.assigned, top.remain.next().assigned);
				if (!top.unify.evaluateDistinct(distincts)) continue;

				// Add a new frame if we still can't properly evaluate the checks
				if (frames.size() < guide.size()) {
					Unification key = top.unify.copy();
					Colut.maskNonNullWithNonNull(key.assigned, masks.get(frames.size()).assigned);
					State next = new State(guide.get(frames.size()).get(key).iterator(), rule.vars);
					Colut.transferNonNull(next.unify.assigned, top.unify.assigned);
					frames.push(next);
					continue;
				}
				
				// Check the atoms and add if successful
				Map<Dob,Dob> unify = top.unify.toMap();
				if (!Terra.checkAtoms(unify, check, truths, pool)) continue;
				result.add(unify);
			}
			
			return result;
		}
		
		private static class State {
			public final Unification unify;
			public final Iterator<Unification> remain;
			public State(Iterator<Unification> remain, ImmutableList<Dob> vars) {
				this.unify = Unification.from(vars);
				this.remain = remain;
			}
		}
	}
	
	/**
	 * Represents the composition of various renderers. If a renderer fails
	 * to render, it will be discarded and the next renderer will take its
	 * place. This renderer will fail when it runs out of renderers.
	 * @author ptpham
	 *
	 */
	public static class Failover extends Renderer {
		public final ImmutableList<Renderer> children;
		private Renderer current;
		
		private Failover(Renderer... children) {
			this.children = ImmutableList.copyOf(children);
			this.current = Colut.any(this.children);
			this.ops.max = this.children.size();
			this.ops.begin();
		}
		
		@Override
		public List<Map<Dob, Dob>> apply(Rule rule, Set<Dob> truths,
				Multimap<Atom, Dob> support, Pool pool) {
			List<Map<Dob,Dob>> result = Lists.newArrayList();
			
			while (true) {
				if (current == null) {
					int pos = (int)this.ops.cur;
					if (this.ops.exceeded()) return result;
					current = this.children.get(pos);
				}
	
				result = current.apply(rule, truths, support, pool);
				if (current.ops.failed) current = null;
				else return result;
			}
		}
	}
	
	public static Multimap<Atom,Dob> getNaiveSupport(Rule rule, Set<Dob> truths) {
		Multimap<Atom,Dob> result = HashMultimap.create();
		for (Atom atom : rule.body) {
			if (!atom.truth) continue;
			result.putAll(atom, truths);
		}
		return result;
	}
	

}
