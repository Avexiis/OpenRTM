### General
- Never leave your generated comments or Javadocs in the code. Leave existing comments, commented out code, and Javadocs alone unless the user requests removal. Java files, to you, are for code only. Comments, notes, and explanations should be placed in `docs/agent-notes`. Generate this folder if not present.
- Only query the networked console when the user gives explicit permission with every new message/prompt.
- Utilize the tool's debugger session for console events if the request involves fixing a console crash, or as-needed when it may be useful. Only do this when you are allowed by the user to connect to the networked console, as described earlier.
- Never use wildcard imports (ie `java.awt.*`) and instead use single class imports.
- Never use fully qualified paths inline unless there are conflict errors such as `java.awt.timer` and `javax.swing.timer` used in one class. Prefer imports.
- Follow `CodeConvention.xml` at the repository root, if present. Temporary unit tests do not need to follow this.
- Remove old unit tests and their folders after use unless the test is user-requested. If the test is user requested, leave it alone.
- Never write developer focused wording into GUI or the user-facing README. This includes method/class/variable names, as well as memory addresses or anything else targeted at developers.
- Use US English - not UK English - for both code naming schema and docs.
- The Xbox 360 has not had an update to its dashboard or kernel since 2019. Versioning related to specific memory addresses is not important, as it is unlikely to ever change again. This extends to games as well.
- If debugging the remote console, do not attempt to read or write to any memory blocks owned by or hooked into by any modules named exactly or similar to the following: `xbguard, cipher, xblghost, proto, xbnetwork, nglive, xblkyuubii, tethered, xbls, nfinite, ninja, xcommunity, snet, myten` without explicit user permission, as doing so may cause anti-tamper actions by the services that represent them.

### Testing
You cannot verify runtime visual behavior or GUI layout yourself, even if you have screen-capture or computer use tools available. 
After completing a task, do not declare it done. Instead:
- Tell the user what to test, including changed behavior, edge cases, etc.
- Wait for the user to confirm the changes are functional before marking the task complete. A clean launch is not a passing test.

### Java Usage
- All code must be Java 17 compatible.
- No use of reflection in any circumstance.
- No executing external processes.
- No downloading or use of dynamic code loading, including classloading.
- No runtime code generation.