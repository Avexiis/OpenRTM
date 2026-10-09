package openrtm.ui;

import openrtm.cod.gsc.GscLanguageSupport;
import org.fife.ui.rsyntaxtextarea.AbstractTokenMaker;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rsyntaxtextarea.TokenMap;

import javax.swing.text.Segment;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class GscTokenMaker extends AbstractTokenMaker
{
	private static final Set<String> NAMES = GscLanguageSupport.completions(Map.of());

	@Override
	public TokenMap getWordsToHighlight()
	{
		return new TokenMap(true);
	}

	@Override
	public String[] getLineCommentStartAndEnd(int languageIndex)
	{
		return new String[]{"//", null};
	}

	@Override
	public boolean getCurlyBracesDenoteCodeBlocks(int languageIndex)
	{
		return true;
	}

	@Override
	public boolean getShouldIndentNextLineAfter(Token token)
	{
		return token != null && token.isSingleChar('{');
	}

	@Override
	public Token getTokenList(Segment text, int initialTokenType, int startOffset)
	{
		resetTokenList();
		int end = text.offset + text.count;
		int index = text.offset;
		int state = initialTokenType;
		while (index < end)
		{
			int start = index;
			int type;
			char value = text.array[index];
			if (state == Token.COMMENT_MULTILINE || state == Token.COMMENT_DOCUMENTATION)
			{
				type = state;
				char closing = state == Token.COMMENT_MULTILINE ? '*' : '#';
				while (index < end && !(index + 1 < end && text.array[index] == closing && text.array[index + 1] == '/'))
				{
					index++;
				}
				if (index < end)
				{
					index += 2;
					state = Token.NULL;
				}
			}
			else if (state != Token.LITERAL_STRING_DOUBLE_QUOTE && value == '/' && index + 1 < end && text.array[index + 1] == '/')
			{
				type = Token.COMMENT_EOL;
				index = end;
			}
			else if (state != Token.LITERAL_STRING_DOUBLE_QUOTE && value == '/' && index + 1 < end && (text.array[index + 1] == '*' || text.array[index + 1] == '#'))
			{
				state = text.array[index + 1] == '*' ? Token.COMMENT_MULTILINE : Token.COMMENT_DOCUMENTATION;
				type = state;
				index += 2;
				char closing = state == Token.COMMENT_MULTILINE ? '*' : '#';
				while (index < end && !(index + 1 < end && text.array[index] == closing && text.array[index + 1] == '/'))
				{
					index++;
				}
				if (index < end)
				{
					index += 2;
					state = Token.NULL;
				}
			}
			else if (value == '"' || state == Token.LITERAL_STRING_DOUBLE_QUOTE)
			{
				type = Token.LITERAL_STRING_DOUBLE_QUOTE;
				if (state != type)
				{
					index++;
				}
				state = type;
				while (index < end)
				{
					char current = text.array[index++];
					if (current == '\\' && index < end)
					{
						index++;
					}
					else if (current == '"')
					{
						state = Token.NULL;
						break;
					}
				}
			}
			else if (Character.isWhitespace(value))
			{
				type = Token.WHITESPACE;
				while (index < end && Character.isWhitespace(text.array[index]))
				{
					index++;
				}
			}
			else if (value == '#')
			{
				type = Token.PREPROCESSOR;
				index = end;
			}
			else if (Character.isLetter(value) || value == '_')
			{
				while (index < end && (Character.isLetterOrDigit(text.array[index]) || text.array[index] == '_'))
				{
					index++;
				}
				String word = new String(text.array, start, index - start).toLowerCase(Locale.ROOT);
				type = GscLanguageSupport.KEYWORDS.contains(word) ? Token.RESERVED_WORD
					: NAMES.contains(word) ? Token.FUNCTION : Token.IDENTIFIER;
			}
			else if (Character.isDigit(value) || value == '.' && index + 1 < end && Character.isDigit(text.array[index + 1]))
			{
				type = Token.LITERAL_NUMBER_FLOAT;
				index++;
				while (index < end && (Character.isLetterOrDigit(text.array[index]) || text.array[index] == '.'))
				{
					index++;
				}
			}
			else
			{
				type = "(){}[];,".indexOf(value) >= 0 ? Token.SEPARATOR : Token.OPERATOR;
				index++;
			}
			addToken(text.array, start, index - 1, type, startOffset + start - text.offset);
		}
		if (state == Token.NULL)
		{
			addNullToken();
		}
		else if (firstToken == null)
		{
			addToken(text.array, text.offset, text.offset - 1, state, startOffset);
		}
		return firstToken;
	}
}
