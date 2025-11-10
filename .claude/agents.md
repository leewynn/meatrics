# Agent Guidelines for Meatrics Project

## Development Workflow Rules

### DO NOT Run Build or Compilation Commands

**Important:** Claude Code agents should **NOT** execute the following commands:
- `mvn compile`
- `mvn clean compile`
- `mvn package`
- `mvn install`
- `mvn test`
- Any other Maven build commands

**Reason:** The user prefers to run compilation and build commands themselves. This allows them to:
- Manage their own development workflow
- Control when builds happen
- Handle any Java version or environment-specific issues
- See build output in their own terminal

### What Agents SHOULD Do

1. **Read and analyze code** to understand the codebase
2. **Edit files** to fix bugs or implement features
3. **Explain changes** clearly so the user understands what was modified
4. **Use Git commands** for status, diff, and commit operations (when requested)
5. **Read documentation** and configuration files
6. **Search for patterns** using Grep and Glob tools

### What Agents SHOULD NOT Do

1. ❌ Run `mvn` commands for compilation or builds
2. ❌ Run `java` commands to start the application
3. ❌ Execute any commands that build or package the application
4. ❌ Try to "test" code by running it

### After Making Code Changes

When you've completed code changes:
1. Summarize what was changed and why
2. Indicate which files were modified
3. Let the user know they can compile and test when ready
4. Do not attempt to compile or verify the changes yourself

### Example Interaction

**Good:**
```
Assistant: I've fixed the GP% tolerance bug in PricingSessionsViewNew.java by changing
the tolerance from 0.001 to 0.1 on lines 326 and 355. This should eliminate the
false positive red/green highlighting. You can now compile and test the changes.
```

**Bad:**
```
Assistant: I've made the fix. Let me compile it to verify...
[Runs mvn compile - DO NOT DO THIS]
```

## Technology Stack Context

This project uses:
- **Vaadin** for UI components
- **jOOQ** for type-safe database access
- **Spring Boot** for application framework
- **PostgreSQL** for database
- **Maven** for build management
- **Java 21+** as the runtime

The user manages their own build and runtime environment.
