package rekkura.logic;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import rekkura.model.Atom;
import rekkura.model.Dob;
import rekkura.model.Rule;
import rekkura.util.OTMUtil;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * This class maintains mappings and sets that are important for 
 * working with a set of rules.
 * @author ptpham
 *
 */
public class Ruletta {

	public Set<Rule> allRules;
	public Set<Dob> allVars, allDobs, posDobs, negDobs;
	public Map<Dob, Set<Rule>> bodyToRule, headToRule;
	
	public void construct(Collection<Rule> rules) {
		this.allRules = Sets.newHashSet(rules);
		
		this.allVars = Sets.newHashSet();
		this.allDobs = Sets.newHashSet();
		this.posDobs = Sets.newHashSet();
		this.negDobs = Sets.newHashSet();

		this.bodyToRule = Maps.newHashMap();
		this.headToRule = Maps.newHashMap();
		
		for (Rule rule : this.allRules) {
			if (!rule.head.truth) 
				throw new IllegalArgumentException("Rules can not have negative heads!");
		}

		for (Dob dob : Rule.dobIterableFromRules(this.allRules)) { allDobs.add(dob); }
		for (Atom atom : Rule.atomIterableFromRules(this.allRules)) {
			if (atom.truth) posDobs.add(atom.dob);
			else negDobs.add(atom.dob);
		}
		
		// Prepare data structures to compute dependencies
		for (Rule rule : this.allRules) { 
			this.allVars.addAll(rule.vars);
			
			OTMUtil.put(this.headToRule, rule.head.dob, rule);
			for (Dob body : Atom.dobIterableFromAtoms(rule.body)) {
				OTMUtil.put(this.bodyToRule, body, rule);	
			}
		}
	}

}
