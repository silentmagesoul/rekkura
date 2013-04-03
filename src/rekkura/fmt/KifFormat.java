package rekkura.fmt;

import java.util.List;
import java.util.Map;
import java.util.Set;

import rekkura.model.Atom;
import rekkura.model.Dob;
import rekkura.model.Rule;
import rekkura.util.Colut;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class KifFormat extends LogicFormat {

	@Override
	public String toString(Dob dob) {
		StringBuilder builder = new StringBuilder();
		append(dob, builder);
		return builder.toString();
	}
	
	private void append(Dob dob, StringBuilder builder) {
		if (dob.isTerminal()) {
			builder.append(dob.name);
			return;
		}
		
		builder.append('(');
		for (int i = 0; i < dob.size(); i++) {
			append(dob.at(i), builder);
			if (i < dob.size() - 1) builder.append(' ');
		}
		builder.append(')');
	}

	@Override
	public Dob dobFromString(String s) {
		s = s.trim();
		if (!s.startsWith("(") || !s.endsWith(")")) return new Dob(s);
		
		s = s.substring(1, s.length() - 1);
		
		List<Dob> children = Lists.newArrayList();
		String[] parts = s.split("\\s+");
		StringBuilder current = new StringBuilder();
		int nest = 0;
		for (String part : parts) {
			current.append(part);
			current.append(' ');
			
			for (char c : part.toCharArray()) {
				if (c == '(') nest++;
				if (c == ')') nest--;
			}
			
			if (nest == 0) {
				String raw = current.toString().trim();
				if (raw.length() == 0) continue;
				children.add(dobFromString(current.toString()));
				current = new StringBuilder();
			}
		}
		
		return new Dob(children);
	}

	@Override
	public String toString(Atom atom) {
		if (atom.truth) return toString(atom.dob);
		return "(not" + toString(atom.dob) + ")";
	}

	@Override
	public Atom atomFromString(String s) {
		Dob dob = dobFromString(s);
		Dob first = dob.at(0);
		if (first.isTerminal() && first.name.equals("not")) {
			Dob second = dob.at(1);
			return new Atom(second, false);
		} else return new Atom(dob, true);
	}

	@Override
	public String toString(Rule rule) {
		Preconditions.checkArgument(rule.head.truth);
		StringBuilder distinct = new StringBuilder();
		if (rule.distinct.size() > 0) distinct.append(' ');
		for (Map.Entry<Dob, Dob> pair : rule.distinct.entrySet()) {
			distinct.append("(distinct ");
			this.append(pair.getKey(), distinct);
			distinct.append(' ');
			this.append(pair.getValue(), distinct);
			distinct.append(")");
		}
		
		return "(<= " + toString(rule.head) + " " 
				+ Joiner.on(" ").join(atomsToStrings(rule.body)) + 
				distinct.toString() + ")";
	}

	@Override
	public Rule ruleFromString(String s) {
		Dob raw = dobFromString(s);
		Preconditions.checkArgument(raw.at(0).isTerminal());
		Preconditions.checkArgument(raw.at(0).name.equals("<="));
		
		Rule result = new Rule();
		result.head = new Atom(raw.at(1), true);
		
		List<Dob> body = Colut.slice(raw.childCopy(), 2, raw.size());
		for (Dob elem : body) {
			if (elem.size() == 3 && elem.at(0).name.equals("distinct")) {
				result.distinct.put(elem.at(1), elem.at(2));
				continue;
			}
			
			Atom converted = atomFromString(toString(elem));
			result.body.add(converted); 
		}
		
		Iterable<Dob> terms = Rule.dobIterableFromRule(result);
		Set<String> seen = Sets.newHashSet();
		for (Dob value : Dob.fullIterable(terms)) {
			String name = value.name;
			if (!value.isTerminal()) continue;
			if (name.length() < 2) continue;
			if (name.charAt(0) != '?') continue;
			if (seen.contains(name)) continue;
			result.vars.add(value);
			seen.add(name);
		}
		
		return result;
	}

}
