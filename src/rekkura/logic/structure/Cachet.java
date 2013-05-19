package rekkura.logic.structure;

import java.util.List;
import java.util.Map;
import java.util.Set;

import rekkura.logic.algorithm.Unifier;
import rekkura.logic.model.Dob;
import rekkura.logic.model.Rule;
import rekkura.logic.model.Logimos.DobSpace;
import rekkura.util.Cache;
import rekkura.util.Colut;
import rekkura.util.OtmUtil;

import com.google.common.base.Function;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;

/**
 * It's french. <br>
 * This class caches a bunch of things that are fun to use in logical manipulation.
 * It started out as a "performance" class, but it's more of a "logical scope" now.
 * @author ptpham
 *
 */
public class Cachet {
	
	/**
	 * This maps ground dobs to their canonical dobs (the dob at 
	 * the end of the unify trunk).
	 */
	public Cache<Dob, Dob> canonicalForms = 
		Cache.create(new Function<Dob, Dob>() {
			@Override public Dob apply(Dob dob) { 
				return Colut.end(rta.fortre.getTrunk(dob));
			}
		});
	
	public Cache<Dob, List<Dob>> canonicalSpines = 
		Cache.create(new Function<Dob, List<Dob>>() {
			@Override public List<Dob> apply(Dob dob) {
				return Lists.newArrayList(rta.fortre.getSpine(dob));
			}
		});
	
	/**
	 * This caches form spines for given canonical dobs.
	 */
	public Cache<Dob, List<Dob>> spines = 
		Cache.create(new Function<Dob, List<Dob>>() {
			@Override public List<Dob> apply(Dob dob) 
			{ return canonicalSpines.get(canonicalForms.get(dob)); }
		});
	
	/**
	 * This caches the list of rules affected by each canonical form.
	 */
	public Cache<Dob, List<Rule>> canonicalRules = 
		Cache.create(new Function<Dob, List<Rule>>() {
			@Override public List<Rule> apply(Dob dob) { 
				return Lists.newArrayList(rta.getAffectedRules(dob));
			}
		});
	
	public Cache<Dob, List<Rule>> affectedRules = 
		Cache.create(new Function<Dob, List<Rule>>() {
			@Override public List<Rule> apply(Dob dob) 
			{ return canonicalRules.get(canonicalForms.get(dob)); }
		});
	
	/**
	 * These hold the mappings from a body form B to grounds 
	 * that are known to successfully unify with B.
	 * Memory is O(FG) but it will only store the things that
	 * are true in any given proving cycle.
	 */
	public final Multimap<Dob, Dob> unisuccess = HashMultimap.create();
		
	public final Ruletta rta;

	public Cachet(Ruletta rta) { this.rta = rta; }
	
	public static class VarAux {
		public final Cache<Dob, List<Dob>> spines;
		
		/**
		 * This holds the mapping from a body form to the sets of replacements
		 * for its various children.
		 * Memory is O(FV).
		 */
		public final Cache<Dob, DobSpace> unispaces = 
			Cache.create(new Function<Dob, DobSpace>() {
				@Override public DobSpace apply(Dob dob) { return new DobSpace(dob); }
			});	
		
		public final Set<Dob> allVars;
	
		public VarAux(Cache<Dob, List<Dob>> spines, Set<Dob> allVars) {
			this.spines = spines;
			this.allVars = allVars;
		}
		
		public void storeVariableReplacements(Dob ground, Dob body) {
			Map<Dob, Dob> unify = Unifier.unify(body, ground);
			DobSpace space = this.unispaces.get(body);
			OtmUtil.putAll(space.replacements, unify);
		}
	}
	
	
	public void storeGround(Dob dob) {
		// The root of the subtree is the end of the trunk.
		Dob end = this.canonicalForms.get(dob);
		if (end != null) this.storeGroundAt(dob, end);
	}
	
	public void storeGroundAt(Dob ground, Dob body) {
		unisuccess.put(body, ground);
	}
}