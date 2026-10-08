package openrtm.cod.gsc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class Iw4GscCompiler
{
	private static final int MAXIMUM_SIZE = 0x37FFF;
	private static final int CODE_ADDRESS = 0x82500500;
	private static final String STANDARD_LIBRARY = "/openrtm/cod/iw4-standard-library.gsc";
	private final Writer output = new Writer();
	private final Map<String, Label> functions = new LinkedHashMap<>();
	private final List<CallFixup> calls = new ArrayList<>();
	private final Map<Integer, Label> callTrampolines = new LinkedHashMap<>();
	private final Map<String, Integer> customTokens = new LinkedHashMap<>();
	private final Map<String, Integer> farCalls = loadCalls("/openrtm/cod/iw4-far-calls.tsv");
	private final Map<String, Integer> stockCalls = loadCalls("/openrtm/cod/iw4-stock-calls.tsv");
	private final List<Iw4GscProgram.StringReference> strings = new ArrayList<>();
	private final Deque<Label> breaks = new ArrayDeque<>();
	private final Deque<Label> continues = new ArrayDeque<>();
	private final List<String> locals = new ArrayList<>();
	private final Map<GscAst.Statement, List<String>> temporaryLocals = new HashMap<>();
	private int generatedNames;

	public static Iw4GscProgram compile(Path selected)
	{
		if (selected == null)
		{
			throw new IllegalArgumentException("Select a GSC file or project folder");
		}
		List<Path> files = sourceFiles(selected.toAbsolutePath().normalize());
		if (files.isEmpty())
		{
			throw new IllegalArgumentException("No GSC files were found");
		}
		List<GscAst.Function> functions = new ArrayList<>();
		for (Path file : files)
		{
			try
			{
				String source = Files.readString(file, StandardCharsets.UTF_8);
				functions.addAll(new GscParser(new GscLexer(source, file.getFileName().toString()).lex())
					.parse().functions());
			}
			catch (IOException failure)
			{
				throw new IllegalArgumentException("Unable to read " + file.getFileName(), failure);
			}
		}
		Set<String> sourceNames = functions.stream()
			.map(function -> normalize(function.name()))
			.collect(Collectors.toSet());
		for (GscAst.Function function : standardFunctions())
		{
			if (!sourceNames.contains(normalize(function.name())))
			{
				functions.add(function);
			}
		}
		return new Iw4GscCompiler().compileFunctions(functions);
	}

	private Iw4GscProgram compileFunctions(List<GscAst.Function> sourceFunctions)
	{
		if (sourceFunctions.isEmpty())
		{
			throw new IllegalArgumentException("The GSC project has no functions");
		}
		Set<String> names = new HashSet<>();
		Map<String, GscAst.Function> available = new LinkedHashMap<>();
		for (GscAst.Function sourceFunction : sourceFunctions)
		{
			String name = normalize(sourceFunction.name());
			if (!names.add(name))
			{
				throw error(sourceFunction.token(), "Duplicate function '" + sourceFunction.name() + "'");
			}
			available.put(name, sourceFunction);
		}
		if (!available.containsKey("init"))
		{
			throw new IllegalArgumentException("The GSC project must contain init()");
		}
		Set<String> reachable = reachableFunctions(available);
		for (String name : reachable)
		{
			functions.put(name, new Label());
		}
		List<GscAst.Function> ordered = sourceFunctions.stream()
			.filter(value -> reachable.contains(normalize(value.name())))
			.collect(Collectors.toCollection(ArrayList::new));
		ordered.sort(Comparator.comparingInt(value -> normalize(value.name()).equals("init") ? 0 : 1));
		for (GscAst.Function sourceFunction : ordered)
		{
			compileFunction(sourceFunction);
		}
		for (Map.Entry<Integer, Label> entry : callTrampolines.entrySet())
		{
			mark(entry.getValue());
			emit(0x54);
			output.alignOperand(4);
			output.writeInt(entry.getKey() - CODE_ADDRESS - output.size() - 4);
		}
		for (CallFixup fixup : calls)
		{
			if (fixup.target().position < 0)
			{
				throw new IllegalStateException("A script function was not emitted");
			}
			int encoded = encodeCall(CODE_ADDRESS + fixup.target().position, fixup.operand());
			output.patch24(fixup.operand(), encoded);
		}
		if (output.size() > MAXIMUM_SIZE)
		{
			throw new IllegalArgumentException("The compiled script is too large");
		}
		return new Iw4GscProgram(output.bytes(), strings);
	}

	private void compileFunction(GscAst.Function sourceFunction)
	{
		locals.clear();
		temporaryLocals.clear();
		functions.get(normalize(sourceFunction.name())).position = output.size();
		for (String parameter : sourceFunction.parameters())
		{
			emitToken(0x67, parameter);
			locals.add(normalize(parameter));
		}
		emit(0x6C);
		Set<String> declared = collectLocals(sourceFunction.body());
		declared.removeAll(locals);
		if (locals.size() + declared.size() > 256)
		{
			throw error(sourceFunction.token(), "Too many local variables in this function");
		}
		for (String name : declared)
		{
			emitToken(0x04, name);
			locals.add(name);
		}
		compileBlock(sourceFunction.body());
		emit(0x00);
	}

	private void compileBlock(GscAst.Block block)
	{
		for (GscAst.Statement statement : block.statements())
		{
			compileStatement(statement);
		}
	}

	private void compileStatement(GscAst.Statement statement)
	{
		if (statement instanceof GscAst.Block block)
		{
			compileBlock(block);
		}
		else if (statement instanceof GscAst.ExpressionStatement expression)
		{
			if (!compileEvent(expression.expression()))
			{
				compileExpression(expression.expression());
				if (expression.expression() instanceof GscAst.Call)
				{
					emit(0x86);
				}
			}
		}
		else if (statement instanceof GscAst.IfStatement conditional)
		{
			compileIf(conditional);
		}
		else if (statement instanceof GscAst.WhileStatement loop)
		{
			compileWhile(loop);
		}
		else if (statement instanceof GscAst.ForStatement loop)
		{
			compileFor(loop);
		}
		else if (statement instanceof GscAst.ForeachStatement loop)
		{
			compileForeach(loop);
		}
		else if (statement instanceof GscAst.SwitchStatement selection)
		{
			compileSwitch(selection);
		}
		else if (statement instanceof GscAst.BreakStatement breaking)
		{
			if (breaks.isEmpty())
			{
				throw error(breaking.token(), "break is only valid in a loop or switch");
			}
			emitJump(0x54, breaks.peek(), 4, false);
		}
		else if (statement instanceof GscAst.ContinueStatement continuing)
		{
			if (continues.isEmpty())
			{
				throw error(continuing.token(), "continue is only valid in a loop");
			}
			emitJump(0x54, continues.peek(), 4, false);
		}
		else if (statement instanceof GscAst.ReturnStatement returning)
		{
			if (returning.value() == null)
			{
				emit(0x00);
			}
			else
			{
				compileExpression(returning.value());
				emit(0x01);
			}
		}
		else if (statement instanceof GscAst.WaitStatement wait)
		{
			compileExpression(wait.value());
			emit(0x85);
		}
		else if (statement instanceof GscAst.WaitFrameStatement)
		{
			emit(0x1E);
		}
	}

	private void compileIf(GscAst.IfStatement conditional)
	{
		Label alternate = new Label();
		Label end = new Label();
		compileExpression(conditional.condition());
		emitJump(0x4F, alternate, 2, true);
		compileStatement(conditional.thenBranch());
		if (conditional.elseBranch() != null)
		{
			emitJump(0x54, end, 4, false);
			mark(alternate);
			compileStatement(conditional.elseBranch());
			mark(end);
		}
		else
		{
			mark(alternate);
		}
	}

	private void compileWhile(GscAst.WhileStatement loop)
	{
		Label start = new Label();
		Label end = new Label();
		mark(start);
		compileExpression(loop.condition());
		emitJump(0x4F, end, 2, true);
		breaks.push(end);
		continues.push(start);
		compileStatement(loop.body());
		continues.pop();
		breaks.pop();
		emitBackJump(start);
		mark(end);
	}

	private void compileFor(GscAst.ForStatement loop)
	{
		if (loop.initializer() != null)
		{
			compileExpression(loop.initializer());
		}
		Label start = new Label();
		Label iterator = new Label();
		Label end = new Label();
		mark(start);
		if (loop.condition() != null)
		{
			compileExpression(loop.condition());
			emitJump(0x4F, end, 2, true);
		}
		breaks.push(end);
		continues.push(iterator);
		compileStatement(loop.body());
		continues.pop();
		breaks.pop();
		mark(iterator);
		if (loop.iterator() != null)
		{
			compileExpression(loop.iterator());
		}
		emitBackJump(start);
		mark(end);
	}

	private void compileForeach(GscAst.ForeachStatement loop)
	{
		List<String> names = temporaryLocals.get(loop);
		String array = names.get(0);
		String key = names.get(1);
		String value = names.get(2);
		assignLocal(array, loop.array(), loop.token());
		assignLocal(key, builtin("getfirstarraykey", List.of(new GscAst.Name(array, loop.token())), loop.token()), loop.token());
		Label start = new Label();
		Label iterator = new Label();
		Label end = new Label();
		mark(start);
		compileExpression(builtin("isdefined", List.of(new GscAst.Name(key, loop.token())), loop.token()));
		emitJump(0x4F, end, 2, true);
		assignLocal(value, new GscAst.Index(new GscAst.Name(array, loop.token()), new GscAst.Name(key, loop.token()),
			loop.token()), loop.token());
		breaks.push(end);
		continues.push(iterator);
		compileStatement(loop.body());
		continues.pop();
		breaks.pop();
		mark(iterator);
		assignLocal(key, builtin("getnextarraykey", List.of(new GscAst.Name(array, loop.token()),
			new GscAst.Name(key, loop.token())), loop.token()), loop.token());
		emitBackJump(start);
		mark(end);
	}

	private void compileSwitch(GscAst.SwitchStatement selection)
	{
		List<String> names = temporaryLocals.get(selection);
		String value = names.get(0);
		String matched = names.get(1);
		assignLocal(value, selection.expression(), selection.token());
		assignLocal(matched, literal(0, selection.token()), selection.token());
		Label end = new Label();
		breaks.push(end);
		GscAst.SwitchCase fallback = null;
		for (GscAst.SwitchCase choice : selection.cases())
		{
			if (choice.value() == null)
			{
				fallback = choice;
				continue;
			}
			Label next = new Label();
			compileExpression(new GscAst.Binary(new GscAst.Name(value, choice.token()), "==", choice.value(),
				choice.token()));
			emitJump(0x4F, next, 2, true);
			assignLocal(matched, literal(1, choice.token()), choice.token());
			for (GscAst.Statement statement : choice.statements())
			{
				compileStatement(statement);
			}
			mark(next);
		}
		if (fallback != null)
		{
			Label skip = new Label();
			compileExpression(new GscAst.Name(matched, fallback.token()));
			emitJump(0x59, skip, 2, true);
			for (GscAst.Statement statement : fallback.statements())
			{
				compileStatement(statement);
			}
			mark(skip);
		}
		breaks.pop();
		mark(end);
	}

	private void compileExpression(GscAst.Expression expression)
	{
		if (expression instanceof GscAst.Literal literal)
		{
			compileLiteral(literal);
		}
		else if (expression instanceof GscAst.Name name)
		{
			compileName(name);
		}
		else if (expression instanceof GscAst.ArrayLiteral array)
		{
			compileArray(array);
		}
		else if (expression instanceof GscAst.VectorLiteral vector)
		{
			compileVector(vector);
		}
		else if (expression instanceof GscAst.Unary unary)
		{
			compileUnary(unary);
		}
		else if (expression instanceof GscAst.Binary binary)
		{
			compileBinary(binary);
		}
		else if (expression instanceof GscAst.Assignment assignment)
		{
			compileAssignment(assignment);
		}
		else if (expression instanceof GscAst.Field field)
		{
			compileField(field);
		}
		else if (expression instanceof GscAst.Index subscript)
		{
			compileIndex(subscript);
		}
		else if (expression instanceof GscAst.Call call)
		{
			compileCall(call);
		}
		else if (expression instanceof GscAst.FunctionReference reference)
		{
			compileFunctionReference(reference);
		}
	}

	private void compileLiteral(GscAst.Literal literal)
	{
		switch (literal.type())
		{
			case UNDEFINED -> emit(0x02);
			case BOOLEAN -> {
				if (literal.value().equalsIgnoreCase("true"))
				{
					emitByte(0x15, 1);
				}
				else
				{
					emit(0x03);
				}
			}
			case INTEGER -> emitInteger(literal.value());
			case FLOAT -> {
				emit(0x1C);
				output.writeFloat(Float.parseFloat(literal.value()));
			}
			case STRING -> emitString(0x1D, literal.value());
			case LOCALIZED_STRING -> emitString(0x2F, literal.value());
		}
	}

	private void compileName(GscAst.Name name)
	{
		String value = normalize(name.value());
		switch (value)
		{
			case "self" -> emit(0x33);
			case "level" -> emit(0x35);
			case "game" -> emit(0x36);
			case "anim" -> emit(0x37);
			case "animation" -> emit(0x38);
			case "thisthread" -> emit(0x34);
			case "undefined" -> emit(0x02);
			default -> emitLocalRead(value, name.token());
		}
	}

	private void compileArray(GscAst.ArrayLiteral array)
	{
		if (!array.values().isEmpty())
		{
			throw error(array.token(), "Array literals may only be empty");
		}
		emit(0x14);
	}

	private void compileVector(GscAst.VectorLiteral vector)
	{
		if (!constantNumber(vector.x()) || !constantNumber(vector.y()) || !constantNumber(vector.z()))
		{
			compileExpression(vector.z());
			compileExpression(vector.y());
			compileExpression(vector.x());
			emit(0x4E);
			return;
		}
		float x = constantFloat(vector.x());
		float y = constantFloat(vector.y());
		float z = constantFloat(vector.z());
		emit(0x30);
		while (output.size() % 4 != 0)
		{
			output.write(0xFF);
		}
		output.writeFloat(x);
		output.writeFloat(y);
		output.writeFloat(z);
	}

	private void compileUnary(GscAst.Unary unary)
	{
		if (unary.operator().equals("++") || unary.operator().equals("--"))
		{
			emitReference(unary.expression(), unary.token());
			emit(unary.operator().equals("++") ? 0x3A : 0x3B);
			emit(0x71);
			return;
		}
		if (unary.operator().equals("-") && unary.expression() instanceof GscAst.Literal literal)
		{
			if (literal.type() == GscAst.LiteralType.INTEGER)
			{
				emitInteger("-" + literal.value());
				return;
			}
			if (literal.type() == GscAst.LiteralType.FLOAT)
			{
				emit(0x1C);
				output.writeFloat(-Float.parseFloat(literal.value()));
				return;
			}
		}
		if (unary.operator().equals("-"))
		{
			emit(0x03);
			compileExpression(unary.expression());
			emit(0x55);
			return;
		}
		compileExpression(unary.expression());
		switch (unary.operator())
		{
			case "!" -> emit(0x8A);
			case "~" -> emit(0x8B);
			case "+" -> {
			}
			default -> throw error(unary.token(), "Unsupported unary operator '" + unary.operator() + "'");
		}
	}

	private void compileBinary(GscAst.Binary binary)
	{
		if (binary.operator().equals("||") || binary.operator().equals("&&"))
		{
			Label end = new Label();
			compileExpression(binary.left());
			emitJump(binary.operator().equals("||") ? 0x44 : 0x3D, end, 2, true);
			compileExpression(binary.right());
			mark(end);
			emit(0x89);
			return;
		}
		compileExpression(binary.left());
		compileExpression(binary.right());
		int opcode = switch (binary.operator())
		{
			case "|" -> 0x3C;
			case "^" -> 0x3E;
			case "&" -> 0x3F;
			case "==" -> 0x40;
			case "!=" -> 0x41;
			case "<" -> 0x42;
			case ">" -> 0x43;
			case "<=" -> 0x45;
			case ">=" -> 0x50;
			case "<<" -> 0x51;
			case ">>" -> 0x52;
			case "+" -> 0x53;
			case "-" -> 0x55;
			case "*" -> 0x56;
			case "/" -> 0x57;
			case "%" -> 0x58;
			default -> throw error(binary.token(), "Unsupported operator '" + binary.operator() + "'");
		};
		emit(opcode);
	}

	private void compileAssignment(GscAst.Assignment assignment)
	{
		if (assignment.operator().equals("="))
		{
			compileExpression(assignment.value());
			emitAssignmentTarget(assignment.target(), assignment.token());
			return;
		}
		compileExpression(assignment.target());
		compileExpression(assignment.value());
		int opcode = switch (assignment.operator())
		{
			case "+=" -> 0x53;
			case "-=" -> 0x55;
			case "*=" -> 0x56;
			case "/=" -> 0x57;
			case "%=" -> 0x58;
			case "|=" -> 0x3C;
			case "^=" -> 0x3E;
			case "&=" -> 0x3F;
			default -> throw error(assignment.token(), "Unsupported assignment operator");
		};
		emit(opcode);
		emitAssignmentTarget(assignment.target(), assignment.token());
	}

	private void compileField(GscAst.Field field)
	{
		if (field.name().equalsIgnoreCase("size"))
		{
			compileExpression(field.target());
			emit(0x5A);
		}
		else if (isName(field.target(), "level"))
		{
			emitToken(0x5E, field.name());
		}
		else if (isName(field.target(), "anim"))
		{
			emitToken(0x5F, field.name());
		}
		else if (isName(field.target(), "self"))
		{
			emitToken(0x60, field.name());
		}
		else
		{
			compileExpression(field.target());
			emit(0x87);
			emitToken(0x61, field.name());
		}
	}

	private void compileIndex(GscAst.Index subscript)
	{
		compileExpression(subscript.index());
		if (subscript.target() instanceof GscAst.Name name)
		{
			int local = localIndex(name.value());
			if (local < 0)
			{
				if (isGlobalName(name.value()))
				{
					compileExpression(name);
					emit(0x0E);
					return;
				}
				throw error(name.token(), "Unknown local variable '" + name.value() + "'");
			}
			emitByte(0x0D, local);
		}
		else
		{
			compileExpression(subscript.target());
			emit(0x0E);
		}
	}

	private void compileCall(GscAst.Call call)
	{
		if (call.pointer() != null)
		{
			if (!threaded(call))
			{
				emit(0x1F);
			}
			emitCallArguments(call);
			compileExpression(call.pointer());
			int opcode;
			if (call.receiver() == null)
			{
				opcode = threaded(call) ? call.childThread() ? 0x28 : 0x27 : 0x22;
			}
			else
			{
				opcode = threaded(call) ? call.childThread() ? 0x2C : 0x2B : 0x24;
			}
			emit(opcode);
			if (threaded(call))
			{
				output.write(call.arguments().size());
			}
			return;
		}
		String name = normalize(call.name());
		if (call.path().isEmpty() && functions.containsKey(name))
		{
			emitScriptCallArguments(call);
			emitScriptCall(call, functions.get(name), -1);
			return;
		}
		if (!call.path().isEmpty())
		{
			String target = normalize(call.path()) + "::" + name;
			Integer operand = farCalls.get(target);
			if (operand == null)
			{
				throw error(call.token(), "Unknown stock script function '" + target + "'");
			}
			emitScriptCallArguments(call);
			emitScriptCall(call, null, operand);
			return;
		}
		Integer stock = stockCalls.get(name);
		if (stock != null)
		{
			emitScriptCallArguments(call);
			emitScriptCall(call, null, stock);
			return;
		}
		int builtin = call.receiver() == null ? Iw4GscTables.function(name)
			: Iw4GscTables.method(name.equals("__openrtm_native_ishost") ? "ishost" : name);
		if (builtin < 0)
		{
			emitScriptCallArguments(call);
			emitScriptCall(call, null, 0xF1F1F1);
			return;
		}
		emitCallArguments(call);
		int arguments = call.arguments().size();
		int base = call.receiver() == null ? 0x77 : 0x7E;
		if (arguments <= 5)
		{
			emit(base + arguments);
		}
		else
		{
			emit(call.receiver() == null ? 0x7D : 0x84);
			output.write(arguments);
		}
		output.writeShort(builtin);
	}

	private void emitScriptCallArguments(GscAst.Call call)
	{
		if (!threaded(call) && (call.receiver() != null || !call.arguments().isEmpty()))
		{
			emit(0x1F);
		}
		emitCallArguments(call);
	}

	private void emitCallArguments(GscAst.Call call)
	{
		for (int i = call.arguments().size() - 1; i >= 0; i--)
		{
			compileExpression(call.arguments().get(i));
		}
		if (call.receiver() != null)
		{
			compileExpression(call.receiver());
		}
	}

	private void emitScriptCall(GscAst.Call call, Label target, int fixedOperand)
	{
		int opcode;
		if (call.receiver() == null)
		{
			opcode = threaded(call) ? call.childThread() ? 0x26 : 0x25
				: call.arguments().isEmpty() ? 0x20 : 0x21;
		}
		else
		{
			opcode = threaded(call) ? call.childThread() ? 0x2A : 0x29 : 0x23;
		}
		int instruction = output.size();
		emit(opcode);
		int operand = output.size();
		output.write24(fixedOperand < 0 ? 0 : relocateCall(fixedOperand, instruction));
		if (target != null)
		{
			calls.add(new CallFixup(target, instruction, operand));
		}
		if (threaded(call))
		{
			output.write(call.arguments().size());
		}
	}

	private void compileFunctionReference(GscAst.FunctionReference reference)
	{
		String name = normalize(reference.name());
		int instruction = output.size();
		emit(0x5C);
		int operand = output.size();
		if (reference.path().isEmpty())
		{
			Label target = functions.get(name);
			if (target == null)
			{
				output.write24(0xF1F1F1);
				return;
			}
			output.write24(0);
			calls.add(new CallFixup(target, instruction, operand));
			return;
		}
		String target = normalize(reference.path()) + "::" + name;
		Integer fixed = farCalls.get(target);
		if (fixed == null)
		{
			throw error(reference.token(), "Unknown stock script function '" + target + "'");
		}
		output.write24(relocateCall(fixed, instruction));
	}

	private boolean compileEvent(GscAst.Expression expression)
	{
		if (!(expression instanceof GscAst.Call call) || call.receiver() == null || !call.path().isEmpty()
			|| call.pointer() != null || threaded(call))
		{
			return false;
		}
		String name = normalize(call.name());
		if (name.equals("notify"))
		{
			emit(0x4B);
			for (int i = call.arguments().size() - 1; i >= 0; i--)
			{
				compileExpression(call.arguments().get(i));
			}
			compileExpression(call.receiver());
			emit(0x49);
			return true;
		}
		if (name.equals("endon"))
		{
			requireArguments(call, 1);
			compileExpression(call.arguments().get(0));
			compileExpression(call.receiver());
			emit(0x4A);
			return true;
		}
		if (name.equals("waittill"))
		{
			if (call.arguments().isEmpty())
			{
				throw error(call.token(), "waittill requires an event name");
			}
			compileExpression(call.arguments().get(0));
			compileExpression(call.receiver());
			emit(0x48);
			for (int i = 1; i < call.arguments().size(); i++)
			{
				GscAst.Expression argument = call.arguments().get(i);
				if (!(argument instanceof GscAst.Name local))
				{
					throw error(argument.token(), "waittill output values must be local variables");
				}
				String value = normalize(local.value());
				if (localIndex(value) < 0)
				{
					throw error(local.token(), "Unknown local variable '" + local.value() + "'");
				}
				emitByte(0x6A, localIndex(value));
			}
			emit(0x6B);
			return true;
		}
		if (name.equals("waittillmatch"))
		{
			requireArguments(call, 1);
			compileExpression(call.arguments().get(0));
			compileExpression(call.receiver());
			emit(0x5B);
			output.writeShort(0);
			emit(0x6B);
			return true;
		}
		return false;
	}

	private void requireArguments(GscAst.Call call, int count)
	{
		if (call.arguments().size() != count)
		{
			throw error(call.token(), call.name() + " requires " + count + " argument");
		}
	}

	private void emitAssignmentTarget(GscAst.Expression target, GscToken token)
	{
		if (target instanceof GscAst.Name name)
		{
			String value = normalize(name.value());
			int index = localIndex(value);
			if (index < 0)
			{
				throw error(token, "Unknown local variable '" + name.value() + "'");
			}
			else if (index == 0)
			{
				emit(0x74);
			}
			else
			{
				emitByte(0x76, index);
			}
			return;
		}
		if (target instanceof GscAst.Field field)
		{
			if (isName(field.target(), "level"))
			{
				emitToken(0x70, field.name());
			}
			else if (isName(field.target(), "anim"))
			{
				emitToken(0x72, field.name());
			}
			else if (isName(field.target(), "self"))
			{
				emitToken(0x73, field.name());
			}
			else
			{
				compileExpression(field.target());
				emit(0x87);
				emitToken(0x65, field.name());
				emit(0x71);
			}
			return;
		}
		if (target instanceof GscAst.Index index)
		{
			compileExpression(index.index());
			emitArrayReference(index.target(), index.token());
			emit(0x71);
			return;
		}
		throw error(token, "Invalid assignment target");
	}

	private void emitReference(GscAst.Expression target, GscToken token)
	{
		if (target instanceof GscAst.Name name)
		{
			if (isName(name, "game"))
			{
				emit(0x39);
				return;
			}
			int index = localIndex(name.value());
			if (index < 0)
			{
				throw error(name.token(), "Unknown local variable '" + name.value() + "'");
			}
			if (index == 0)
			{
				emit(0x6D);
			}
			else
			{
				emitByte(0x6F, index);
			}
			return;
		}
		if (target instanceof GscAst.Field field)
		{
			if (isName(field.target(), "level"))
			{
				emitToken(0x62, field.name());
			}
			else if (isName(field.target(), "anim"))
			{
				emitToken(0x63, field.name());
			}
			else if (isName(field.target(), "self"))
			{
				emitToken(0x64, field.name());
			}
			else
			{
				compileExpression(field.target());
				emit(0x87);
				emitToken(0x65, field.name());
			}
			return;
		}
		if (target instanceof GscAst.Index index)
		{
			compileExpression(index.index());
			emitArrayReference(index.target(), index.token());
			return;
		}
		throw error(token, "Invalid variable reference");
	}

	private void emitArrayReference(GscAst.Expression target, GscToken token)
	{
		if (target instanceof GscAst.Name name)
		{
			int index = localIndex(name.value());
			if (index < 0)
			{
				if (isName(name, "game"))
				{
					emit(0x39);
					emit(0x12);
					return;
				}
				throw error(token, "Unknown local variable '" + name.value() + "'");
			}
			if (index == 0)
			{
				emit(0x0F);
			}
			else
			{
				emitByte(0x11, index);
			}
		}
		else
		{
			emitReference(target, token);
			emit(0x12);
		}
	}

	private void emitLocalRead(String name, GscToken token)
	{
		int index = localIndex(name);
		if (index < 0)
		{
			throw error(token, "Unknown local variable '" + name + "'");
		}
		if (index <= 5)
		{
			emit(0x06 + index);
		}
		else
		{
			emitByte(0x0C, index);
		}
	}

	private int localIndex(String name)
	{
		String value = normalize(name);
		for (int i = locals.size() - 1; i >= 0; i--)
		{
			if (locals.get(i).equals(value))
			{
				return locals.size() - 1 - i;
			}
		}
		return -1;
	}

	private void assignLocal(String name, GscAst.Expression value, GscToken token)
	{
		compileAssignment(new GscAst.Assignment(new GscAst.Name(name, token), "=", value, token));
	}

	private void emitInteger(String value)
	{
		long parsed = Long.decode(value);
		if (parsed == 0)
		{
			emit(0x03);
		}
		else if (parsed > 0 && parsed <= 0xFF)
		{
			emitByte(0x15, (int) parsed);
		}
		else if (parsed < 0 && parsed >= -0xFF)
		{
			emitByte(0x16, (int) -parsed);
		}
		else if (parsed > 0 && parsed <= 0xFFFF)
		{
			emit(0x17);
			output.writeShort((int) parsed);
		}
		else if (parsed < 0 && parsed >= -0xFFFF)
		{
			emit(0x18);
			output.writeShort((int) -parsed);
		}
		else
		{
			emit(0x19);
			output.writeInt((int) parsed);
		}
	}

	private void emitString(int opcode, String value)
	{
		emit(opcode);
		output.alignOperand(2);
		int offset = output.size();
		output.writeShort(0);
		strings.add(new Iw4GscProgram.StringReference(offset, value));
	}

	private void emitToken(int opcode, String name)
	{
		String value = normalize(name);
		int token = Iw4GscTables.token(value);
		if (token < 0)
		{
			token = customTokens.computeIfAbsent(value, ignored -> 0xF35 + customTokens.size());
		}
		emit(opcode);
		output.writeShort(token);
	}

	private void emit(int opcode)
	{
		output.write(opcode);
	}

	private void emitByte(int opcode, int value)
	{
		emit(opcode);
		output.write(value);
	}

	private void emitJump(int opcode, Label target, int width, boolean relativeToThree)
	{
		int instruction = output.size();
		emit(opcode);
		output.alignOperand(width);
		int operand = output.size();
		if (width == 2)
		{
			output.writeShort(0);
		}
		else
		{
			output.writeInt(0);
		}
		target.jumps.add(new JumpFixup(instruction, operand, width, relativeToThree));
		if (target.position >= 0)
		{
			patchJump(target, target.jumps.remove(target.jumps.size() - 1));
		}
	}

	private void emitBackJump(Label target)
	{
		if (target.position < 0)
		{
			throw new IllegalStateException("A loop target was not emitted");
		}
		emit(0x46);
		output.alignOperand(2);
		output.writeShort(output.size() + 2 - target.position);
	}

	private void mark(Label label)
	{
		if (label.position >= 0)
		{
			throw new IllegalStateException("A script label was emitted twice");
		}
		label.position = output.size();
		for (JumpFixup jump : label.jumps)
		{
			patchJump(label, jump);
		}
		label.jumps.clear();
	}

	private void patchJump(Label label, JumpFixup jump)
	{
		int distance = label.position - jump.operand() - jump.width();
		if (jump.width() == 2)
		{
			if (distance < Short.MIN_VALUE || distance > Short.MAX_VALUE)
			{
				throw new IllegalArgumentException("A script branch is too large");
			}
			output.patchShort(jump.operand(), distance);
		}
		else
		{
			output.patchInt(jump.operand(), distance);
		}
	}

	private static List<Path> sourceFiles(Path selected)
	{
		if (!Files.exists(selected))
		{
			throw new IllegalArgumentException("The selected GSC path does not exist");
		}
		if (Files.isRegularFile(selected))
		{
			if (!selected.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".gsc"))
			{
				throw new IllegalArgumentException("Select a .gsc file or project folder");
			}
			Path parent = selected.getParent();
			if (parent != null && Files.isRegularFile(parent.resolve("config.il")))
			{
				return gscFiles(parent);
			}
			return List.of(selected);
		}
		return gscFiles(selected);
	}

	private static List<Path> gscFiles(Path directory)
	{
		try (Stream<Path> files = Files.list(directory))
		{
			return files.filter(Files::isRegularFile)
				.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".gsc"))
				.sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
				.toList();
		}
		catch (IOException failure)
		{
			throw new IllegalArgumentException("Unable to read the selected GSC project", failure);
		}
	}

	private static Set<String> reachableFunctions(Map<String, GscAst.Function> available)
	{
		Set<String> reachable = new HashSet<>();
		Deque<String> pending = new ArrayDeque<>();
		pending.add("init");
		while (!pending.isEmpty())
		{
			String name = pending.removeFirst();
			if (!reachable.add(name))
			{
				continue;
			}
			Set<String> referenced = new HashSet<>();
			collectReferences(available.get(name).body(), referenced);
			for (String reference : referenced)
			{
				if (available.containsKey(reference) && !reachable.contains(reference))
				{
					pending.addLast(reference);
				}
			}
		}
		return reachable;
	}

	private static void collectReferences(GscAst.Statement statement, Set<String> references)
	{
		GscAst.visit(statement, ignored -> {}, expression -> {
			if (expression instanceof GscAst.Call call && call.path().isEmpty() && !call.name().isEmpty())
			{
				references.add(normalize(call.name()));
			}
			else if (expression instanceof GscAst.FunctionReference reference && reference.path().isEmpty())
			{
				references.add(normalize(reference.name()));
			}
		});
	}

	private Set<String> collectLocals(GscAst.Statement body)
	{
		Set<String> declared = new LinkedHashSet<>();
		GscAst.visit(body, statement -> {
			List<String> names;
			if (statement instanceof GscAst.ForeachStatement loop)
			{
				names = List.of(generated("array"), loop.value() == null ? generated("key") : loop.variable(),
					loop.value() == null ? loop.variable() : loop.value());
			}
			else if (statement instanceof GscAst.SwitchStatement)
			{
				names = List.of(generated("switch"), generated("matched"));
			}
			else
			{
				return;
			}
			temporaryLocals.put(statement, names);
			for (String name : names)
			{
				declared.add(normalize(name));
			}
		}, expression -> {
			if (expression instanceof GscAst.Assignment assignment)
			{
				GscAst.Expression target = assignment.target();
				while (target instanceof GscAst.Index index)
				{
					target = index.target();
				}
				if (target instanceof GscAst.Name name && !isGlobalName(name.value()))
				{
					declared.add(normalize(name.value()));
				}
			}
			else if (expression instanceof GscAst.Call call && call.receiver() != null && call.path().isEmpty()
				&& call.pointer() == null && !threaded(call) && normalize(call.name()).equals("waittill"))
			{
				for (int index = 1; index < call.arguments().size(); index++)
				{
					if (call.arguments().get(index) instanceof GscAst.Name name)
					{
						declared.add(normalize(name.value()));
					}
				}
			}
		});
		return declared;
	}

	private static Map<String, Integer> loadCalls(String resource)
	{
		InputStream input = Iw4GscCompiler.class.getResourceAsStream(resource);
		if (input == null)
		{
			throw new ExceptionInInitializerError("Missing GSC call table " + resource);
		}
		Map<String, Integer> values = new HashMap<>();
		try
		{
			for (String line : new String(input.readAllBytes(), StandardCharsets.US_ASCII).split("\\R"))
			{
				if (!line.isBlank())
				{
					String[] parts = line.split("\\t", 2);
					values.put(normalize(parts[1]), Integer.parseUnsignedInt(parts[0], 16));
				}
			}
		}
		catch (IOException failure)
		{
			throw new ExceptionInInitializerError(failure);
		}
		return Map.copyOf(values);
	}

	private static List<GscAst.Function> standardFunctions()
	{
		InputStream input = Iw4GscCompiler.class.getResourceAsStream(STANDARD_LIBRARY);
		if (input == null)
		{
			throw new ExceptionInInitializerError("Missing GSC standard library");
		}
		try (input)
		{
			String source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
			return new GscParser(new GscLexer(source, "standard library").lex()).parse().functions();
		}
		catch (IOException failure)
		{
			throw new IllegalStateException("Unable to read the GSC standard library", failure);
		}
	}

	private int relocateCall(int canonical, int instruction)
	{
		if (canonical == 0xF1F1F1)
		{
			return canonical;
		}
		int originalAddress = CODE_ADDRESS + 2;
		int target = originalAddress + (((canonical << 8) + originalAddress) >> 12);
		int distance = target - CODE_ADDRESS - instruction - 1;
		if (distance < -0x80000 || distance > 0x7FFFF)
		{
			Label trampoline = callTrampolines.computeIfAbsent(target, ignored -> new Label());
			calls.add(new CallFixup(trampoline, instruction, instruction + 1));
			return 0;
		}
		return encodeCall(target, instruction + 1);
	}

	private static int encodeCall(int target, int operand)
	{
		int address = CODE_ADDRESS + operand;
		int distance = target - address;
		int encoded = (((distance << 12) - address + 255) >>> 8) & 0xFFFFFF;
		if (address + (((encoded << 8) + address) >> 12) != target)
		{
			throw new IllegalArgumentException("A script call is out of range");
		}
		return encoded;
	}

	private static boolean threaded(GscAst.Call call)
	{
		return call.thread() || call.childThread();
	}

	private static String normalize(String value)
	{
		return value.toLowerCase(Locale.ROOT);
	}

	private static IllegalArgumentException error(GscToken token, String message)
	{
		return new IllegalArgumentException(token.location() + ": " + message);
	}

	private String generated(String role)
	{
		return "__openrtm_" + role + generatedNames++;
	}

	private static GscAst.Call builtin(String name, List<GscAst.Expression> arguments, GscToken token)
	{
		return new GscAst.Call(null, "", name, null, arguments, false, false, token);
	}

	private static GscAst.Literal literal(int value, GscToken token)
	{
		return new GscAst.Literal(GscAst.LiteralType.INTEGER, Integer.toString(value), token);
	}

	private static boolean isName(GscAst.Expression expression, String value)
	{
		return expression instanceof GscAst.Name name && normalize(name.value()).equals(value);
	}

	private static boolean isGlobalName(String value)
	{
		return Set.of("self", "level", "game", "anim", "animation", "thisthread").contains(normalize(value));
	}

	private static float constantFloat(GscAst.Expression expression)
	{
		if (expression instanceof GscAst.Literal literal
			&& (literal.type() == GscAst.LiteralType.FLOAT || literal.type() == GscAst.LiteralType.INTEGER))
		{
			return Float.parseFloat(literal.value());
		}
		if (expression instanceof GscAst.Unary unary && unary.operator().equals("-")
			&& unary.expression() instanceof GscAst.Literal literal)
		{
			return -Float.parseFloat(literal.value());
		}
		throw error(expression.token(), "Vector components must be constant numbers");
	}

	private static boolean constantNumber(GscAst.Expression expression)
	{
		return expression instanceof GscAst.Literal literal
			&& (literal.type() == GscAst.LiteralType.FLOAT || literal.type() == GscAst.LiteralType.INTEGER)
			|| expression instanceof GscAst.Unary unary && unary.operator().equals("-")
			&& unary.expression() instanceof GscAst.Literal operand
			&& (operand.type() == GscAst.LiteralType.FLOAT || operand.type() == GscAst.LiteralType.INTEGER);
	}

	private record CallFixup(Label target, int instruction, int operand)
	{
	}

	private record JumpFixup(int instruction, int operand, int width, boolean relativeToThree)
	{
	}

	private static final class Label
	{
		private int position = -1;
		private final List<JumpFixup> jumps = new ArrayList<>();
	}

	private static final class Writer
	{
		private byte[] buffer = new byte[4096];
		private int size;

		int size()
		{
			return size;
		}

		void write(int value)
		{
			ensure(1);
			buffer[size++] = (byte) value;
		}

		void writeShort(int value)
		{
			alignOperand(2);
			write(value >>> 8);
			write(value);
		}

		void write24(int value)
		{
			write(value >>> 16);
			write(value >>> 8);
			write(value);
		}

		void writeInt(int value)
		{
			alignOperand(4);
			write(value >>> 24);
			write(value >>> 16);
			write(value >>> 8);
			write(value);
		}

		void writeFloat(float value)
		{
			while (size % 4 != 0)
			{
				write(0xFF);
			}
			writeInt(Float.floatToIntBits(value));
		}

		void alignOperand(int width)
		{
			while ((size & 31) + width > 32)
			{
				write(0xFF);
			}
		}

		void patchShort(int offset, int value)
		{
			buffer[offset] = (byte) (value >>> 8);
			buffer[offset + 1] = (byte) value;
		}

		void patch24(int offset, int value)
		{
			buffer[offset] = (byte) (value >>> 16);
			buffer[offset + 1] = (byte) (value >>> 8);
			buffer[offset + 2] = (byte) value;
		}

		void patchInt(int offset, int value)
		{
			buffer[offset] = (byte) (value >>> 24);
			buffer[offset + 1] = (byte) (value >>> 16);
			buffer[offset + 2] = (byte) (value >>> 8);
			buffer[offset + 3] = (byte) value;
		}

		byte[] bytes()
		{
			byte[] values = new byte[size];
			System.arraycopy(buffer, 0, values, 0, size);
			return values;
		}

		private void ensure(int length)
		{
			if (size + length <= buffer.length)
			{
				return;
			}
			byte[] expanded = new byte[Math.max(buffer.length * 2, size + length)];
			System.arraycopy(buffer, 0, expanded, 0, size);
			buffer = expanded;
		}
	}
}
