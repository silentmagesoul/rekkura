package rekkura.logic.algorithm;

import java.util.*;

import rekkura.logic.model.Atom;
import rekkura.logic.model.Dob;
import rekkura.logic.model.Rule;
import rekkura.logic.model.Unification;
import rekkura.logic.structure.Pool;
import rekkura.util.Cartesian;
import rekkura.util.Cartesian.AdvancingIterator;
import rekkura.util.Colut;
import rekkura.util.Limiter;

import com.google.common.collect.*;

/**
 * This class holds a collection of utilities for generating
 * and using groundings. In general, a "support" is a Multimap
 * that maps from Atoms in the body of a {@link Rule} to the 
 * groundings that might unify with that atom.
 * @author ptpham
 *
 */
public class Terra {
	public static AdvancingIterator<Unification> getUnificationIterator(Rule rule,
		List<Atom> expanders, Multimap<Atom, Dob> support, Set<Dob> truths) {
		if (rule.vars.size() == 0) return Cartesian.emptyIterator();
		if (expanders == null) return Cartesian.emptyIterator();

		// Construct iterator and expand
		List<List<Unification>> space = getUnificationSpace(rule, support, expanders);
		return Cartesian.asIterator(space);
	}
	
	/**
	 * Returns a comparator that sorts in increasing order of
	 * overlap with vars. 
	 * @param vars
	 * @param other
	 * @return
	 */
	public static Comparator<Atom> getOverlapComparator(final Collection<Dob> vars) {
		return new Comparator<Atom>() {
			@Override public int compare(Atom first, Atom second) {
				int left = Colut.countIn(first.dob.fullIterable(), vars);
				int right = Colut.countIn(second.dob.fullIterable(), vars);
				return left - right;
			}
		};
	}

	/**
	 * Selects a subset of the atoms in the body of a rule for expansion based
	 * on the minimum cost. Stops once all variables are covered.
	 * @param rule
	 * @param costs
	 * @return
	 */
	public static List<Atom> getGreedyExpanders(Rule rule, Map<Atom,Integer> costs) {
		// Sort the dimensions of the space so that the smallest ones come first.
		List<Atom> positives = Atom.filterPositives(rule.body);
		Colut.sortByMap(positives, costs, 0);
		
		// Then greedily find a variable cover and resort for the final support
		List<Atom> expanders = Terra.getVarCover(positives, rule.vars);
		if (expanders == null) return null;
		
		// Prioritize variables in the head
		List<Comparator<Atom>> comparators = Lists.newArrayList();
		comparators.add(getPresenceComparator(Lists.newArrayList(rule.head.dob.fullIterable())));
		
		// Prioritize distincts
		if (rule.distinct.size() > 0) {
			List<Dob> vars = Colut.intersect(Rule.dobIterableFromDistincts(rule.distinct), rule.vars);
			comparators.add(getPresenceComparator(vars));
		}
		
		Collections.sort(expanders, Ordering.compound(comparators));
		return expanders;
	}
	
	/**
	 * Compares such that atoms that contain the given dobs come before
	 * the onese that do not.
	 * @param targets
	 * @return
	 */
	public static Comparator<Atom> getPresenceComparator(final Collection<Dob> targets) {
		return new Comparator<Atom>() {
			@Override public int compare(Atom left, Atom right) {
				boolean first = Colut.containsAny(left.dob.fullIterable(), targets);
				boolean second = Colut.containsAny(right.dob.fullIterable(), targets);
				if (first == second) return 0;
				if (first) return -1;
				return 1;
			}
		};
	}
	
	public static List<Map<Dob, Dob>> expandUnifications(Rule rule, List<Atom> check,
		Cartesian.AdvancingIterator<Unification> iterator, Pool pool, Set<Dob> truths) {
		Limiter.Operations limiter = Limiter.forOperations();
		return expandUnifications(rule, check, iterator, pool, truths, limiter);
	}

	/**
	 * This is the central loop in the logic package. It will iterate through 
	 * the provided unification lists in the given iterator. The unifications
	 * in each list will be combined until one of two cases occurs. If the 
	 * unification fails, then the iterator will be advanced in the failing
	 * position. If the unification succeeds, then a submerged unification map
	 * will be constructed and added to the result.
	 * @param rule
	 * @param check once a unification list is merged into a unification, these
	 * atoms will be unified with that unification and checked for existence in
	 * the provided truth dobs. The idea is that these atoms were not used in
	 * the construction of the unification list and therefore need to be checked
	 * externally.
	 * @param iterator
	 * @param pool
	 * @param truths
	 * @param limiter
	 * @return
	 */
	public static List<Map<Dob, Dob>> expandUnifications(Rule rule,
		List<Atom> check, Cartesian.AdvancingIterator<Unification> iterator, Pool pool,
		Set<Dob> truths, Limiter.Operations limiter) {
		List<Map<Dob,Dob>> result = Lists.newArrayList();
		Unification unify = Unification.from(rule.vars);
		if (applyVarless(rule, truths, result)) return result;
		
		List<Unification.Distinct> distincts = Unification.convert(rule.distinct, rule.vars);
		while (iterator.hasNext() && !limiter.exceeded()) {
			unify.clear();

			// Dobs in the variable cover must contribute in a
			// non conflicting way to the unification.
			int failure = -1;
			List<Unification> assignment = iterator.next();
			failure = unify.sloppyDirtyMergeWith(assignment, distincts);
			
			// Verify that the atoms that did not participate in the unification
			// have their truth values satisfied.
			Map<Dob, Dob> converted = failure == -1 ? unify.toMap() : null;
			if (converted != null && check.size() > 0) {
				if (!checkAtoms(converted, check, truths, pool)) continue;
			}
			
			// Final check for distincts before rendering head
			if (converted != null && unify.isValid()) {
				result.add(converted);
			} else if (failure >= 0) {
				iterator.advance(failure);
			} 
		}
		return result;
	}

	/**
	 * This method can be used to handle the vacuous/varless rule special case.
	 * @param rule
	 * @param truths
	 * @return
	 */
	public static boolean applyVarless(Rule rule, Set<Dob> truths, List<Map<Dob, Dob>> result) {
		if (rule.vars.size() == 0) {
			if(checkGroundAtoms(rule.body, truths)) {
				result.add(Maps.<Dob,Dob>newHashMap());
				return true;
			}
		}
		return false;
	}

	public static Dob applyVarless(Rule rule, Set<Dob> truths, Pool pool) {
		List<Map<Dob,Dob>> result = Lists.newArrayList();
		if (applyVarless(rule, truths, result)) return pool.render(rule.head.dob, Colut.any(result));
		return null;
	}
	
	public static List<Atom> getVarCover(Iterable<Atom> atoms, Iterable<Dob> vars) {
		List<Dob> remaining = Lists.newArrayList(vars);
		List<Atom> result = Lists.newArrayList();
		
		for (Atom atom : atoms) {
			if (remaining.size() == 0) break;
			if (Colut.removeAll(remaining, atom.dob.fullIterable())) {
				result.add(atom);
			}
		}
		
		if (remaining.size() > 0) return null;
		return result;
	}
	
	/**
	 * Generates a variable cover that greedily selects in each iteration
	 * the atom that covers first the most already covered variables and
	 * then the least uncovered variables.
	 * @param atoms
	 * @param vars
	 * @return
	 */
	public static List<Atom> getChainingCover(Iterable<Atom> atoms, Collection<Dob> vars) {
		List<Atom> available = Lists.newArrayList(atoms);
		Set<Dob> covered = Sets.newHashSet();
		List<Atom> result = Lists.newArrayList();

		List<Comparator<Atom>> comparators = Lists.newArrayList();		
		comparators.add(getOverlapComparator(covered));
		comparators.add(Collections.reverseOrder(getOverlapComparator(vars)));
		Comparator<Atom> comparator = Ordering.compound(comparators);

		while (covered.size() < vars.size() && available.size() > 0) {
			Atom next = Collections.max(available, comparator);
			
			available.remove(next);
			if (covered.addAll(Colut.intersect(next.dob.fullIterable(), vars))) {
				result.add(next);
			}
		}
		
		if (covered.size() < vars.size()) return null;
		return result;
	}
	
	public static List<Atom> getChainingMarginCover(Rule rule) {
		List<Atom> positives = Atom.filterPositives(rule.body);
		List<Dob> headVars = Colut.intersect(rule.head.dob.fullIterable(), rule.vars);
		return Terra.getChainingMarginCover(positives, headVars, rule.vars);
	}
	
	public static List<Atom> getChainingMarginCover(Iterable<Atom> candidates, Collection<Dob> targets, Collection<Dob> vars) {
		// Find a covering for the head so that we can put that at the beginning.
		// This allows us to marginalize more effectively.
		List<Atom> all = Lists.newArrayList(candidates);
		Comparator<Atom> tarcomp = Terra.getOverlapComparator(targets);
		Collections.sort(all, Collections.reverseOrder(tarcomp));
		List<Atom> tarcov = Terra.getVarCover(all, targets);
		if (tarcov == null) return null;
		
		// Cover the remaining variables
		Colut.removeAll(all, tarcov);
		List<Dob> remain = Lists.newArrayList(vars);
		for (Atom term : tarcov) Colut.removeAll(remain, term.dob.fullIterable());
		List<Atom> expanders = Terra.getChainingCover(all, remain);
		if (expanders == null) return null;
		expanders.addAll(0, tarcov);
		return expanders;
	}
	
	public static boolean checkGroundAtoms(Iterable<Atom> body, Set<Dob> truths) {
		for (Atom atom : body) {
			boolean truth = truths.contains(atom.dob);
			if (atom.truth ^ truth) return false;
		}
		return true;
	}
	
	public static Set<Dob> renderHeads(Iterable<Map<Dob,Dob>> unifies, Rule rule, Pool pool) {
		Set<Dob> result = Sets.newHashSet();
		for (Map<Dob,Dob> unify : unifies) result.add(pool.render(rule.head.dob, unify));
		return result;
	}
	
	/**
	 * Attempts to generate a rule's head with the given assignment to the rule's body
	 * and the given set of things that are currently true.
	 * @param rule
	 * @param bodies these should be in order of the positive atoms in the rule
	 * @param truths
	 * @param pool
	 * @return
	 */
	public static Dob applyBodies(Rule rule, List<Dob> bodies, Set<Dob> truths, Pool pool) {
		Dob varless = applyVarless(rule, truths, pool);
		if (varless != null) return varless;
		List<Dob> dobs = Atom.asDobList(Atom.filterPositives(rule.body));
		Map<Dob, Dob> unify = Unifier.unifyListVars(dobs, bodies, rule.vars);
		if (!checkAtoms(unify, Atom.filterNegatives(rule.body), truths, pool)) return null;
		if (!rule.evaluateDistinct(unify)) return null;
		return pool.render(rule.head.dob, unify);
	}

	/**
	 * Returns true if the unification satisfies the atoms that
	 * need to be checked.
	 * @param unify
	 * @param atoms
	 * @param truths
	 * @param pool
	 * @return
	 */
	public static boolean checkAtoms(Map<Dob, Dob> unify,
			List<Atom> atoms, Set<Dob> truths, Pool pool) {
		for (Atom atom : atoms) {
			Dob generated = pool.dobs.submerge(Unifier.replace(atom.dob, unify));
			if (generated == null || truths.contains(generated) != atom.truth) return false;
		}
		return true;
	}
	
	/**
	 * Converts a basic support to a more performant representation.
	 * Each inner list at position i corresponds to the unifications
	 * that succeeded with the atom at position i in the body of the rule.
	 * @param rule
	 * @param support
	 * @param positives the list of atoms we actually want to keep from the
	 * support
	 * @return
	 */
	public static List<List<Unification>> getUnificationSpace(Rule rule,
		final Multimap<Atom, Dob> support, List<Atom> positives) {
		
		List<List<Unification>> result = Lists.newArrayList();
 		for (Atom atom : positives) {
			Collection<Dob> grounds = support.get(atom);
			List<Unification> unifies = Lists.newArrayList();
			for (Dob ground : grounds) {
				Map<Dob, Dob> unify = Unifier.unifyVars(atom.dob, ground, rule.vars);
				Unification wrapped = unify == null ? null : Unification.from(unify, rule.vars);
				if (wrapped != null) unifies.add(wrapped); 
			}
			result.add(unifies);
		}
		return result;
	}
	
	/**
	 * This method attempts to apply candidates as variables. The order used
	 * is the order of the variables in the rule.
	 * @param rule
	 * @param candidates
	 * @param truths
	 * @param pool
	 * @return
	 */
	public static Map<Dob, Dob> applyVars(Rule rule, List<Dob> candidates, 
			Set<Dob> truths, Pool pool) {
		Map<Dob, Dob> unify = Maps.newHashMap();
		List<Dob> vars = rule.vars; 
		List<Atom> body = rule.body;
		
		// Construct replacement
		if (rule.vars.size() != candidates.size()) return null;
		for (int i = 0; i < vars.size(); i++) {
			unify.put(vars.get(i), candidates.get(i));
		}
		
		if (!checkAtoms(unify, body, truths, pool)) return null;
		if (!rule.evaluateDistinct(unify)) return null;
		return unify;
	}

	public static HashMultimap<Dob,Dob> indexBy(Iterable<Dob> dobs, Collection<Dob> targets) {
		HashMultimap<Dob,Dob> result = HashMultimap.create();
		for (Dob dob : dobs) {
			for (Dob child : dob.fullIterable()) {
				if (targets.contains(child)) result.put(child, dob);
			}
		}
		return result;
	}
	
	public static ArrayListMultimap<Dob,Unification> indexBy(Iterable<Unification> slice, int pos) {
		ArrayListMultimap<Dob,Unification> result = ArrayListMultimap.create();
		for (Unification unify : slice) result.put(unify.assigned[pos], unify);
		return result;
	}
	
	/**
	 * This method returns variables that are shared between at least two of the
	 * given terms. They are sorted in decreasing order of overlap over terms and
	 * variables with no overlap are not returned.
	 * @param terms
	 * @param vars
	 * @return
	 */
	public static List<Dob> getPartitionCandidates(Iterable<Dob> terms, Collection<Dob> vars) {
		Multiset<Dob> counts = HashMultiset.create();
		for (Dob dob : terms) {
			Set<Dob> all = Sets.newHashSet(dob.fullIterable());
			for (Dob var : vars) if (all.contains(var)) counts.add(var);
		}
		
		List<Dob> result = Lists.reverse(Colut.sortByCount(counts));
		for (int i = result.size() - 1; i > 0; i--) {
			if (counts.count(result.get(i)) < 2) result.remove(i);
		}
		return result;
	}
}
