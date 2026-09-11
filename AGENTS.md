### General
- Never leave comments or Javadocs in code. .java files are for code only, comments, notes, and explanations should be placed in `docs/agent-notes`. Generate this folder if not present.
- Only query the networked console when the user gives explicit permission with every new message/prompt.
- Utilize the tool's debugger session for console events if the request involves fixing a console crash, or as-needed when it may be useful. Only do this when you are allowed by the user to connect to the networked console, as described earlier.
- Never use wildcard imports (ie `java.awt.*`) and instead use single class imports.
- Never use fully qualified paths inline unless there are conflict errors such as `java.awt.timer` and `javax.swing.timer` used in one class. Prefer imports.
- Follow `CodeConvention.xml` at the repository root, if present.
- Remove old unit tests and their folders after use unless the test is user-requested. If the test is user requested, leave it alone.
- Never write developer focused wording into GUI or the user-facing README. This includes method/class/variable names, as well as memory addresses or anything else targeted at developers.
- Use US English - not UK English - for both code naming schema and docs.

### Testing
You cannot verify runtime visual behavior or GUI layout yourself, even if you have screen-capture or computer use tools available. 
After completing a task, do not declare it done. Instead:
- Tell the user what to test, including changed behavior, edge cases, etc.
- Wait for the user to confirm the changes are functional before marking the task complete. A clean launch is not a passing test.

### Java Usage
- All code must be Java 11 compatible.
- No use of reflection in any circumstance.
- No executing external processes.
- No downloading or use of dynamic code loading, including classloading.
- No runtime code generation.