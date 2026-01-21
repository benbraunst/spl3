package bgu.spl.net.impl.data;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

public class Database {
	private final ConcurrentHashMap<String, User> userMap;
	private final ConcurrentHashMap<Integer, User> connectionsIdMap;
	private final String sqlHost;
	private final int sqlPort;

	private Database() {
		userMap = new ConcurrentHashMap<>();
		connectionsIdMap = new ConcurrentHashMap<>();
		// SQL server connection details
		this.sqlHost = "127.0.0.1";
		this.sqlPort = 7778;
		
		// Clean up any incomplete login sessions from previous server run
		cleanupIncompleteSessions();
	}
	
	/**
	 * Mark all login sessions without logout_time as logged out
	 * Handles cases where the server crashed or was restarted
	 */
	private void cleanupIncompleteSessions() {
		System.out.println("[Database] Cleaning up incomplete login sessions from previous server run...");
		String cleanupSQL = "UPDATE login_history SET logout_time=datetime('now') WHERE logout_time IS NULL";
		String result = executeSQL(cleanupSQL);
		
		if (result.startsWith("SUCCESS")) {
			// Parse number of rows affected
			String[] parts = result.split(":");
			if (parts.length > 1) {
				String rowsAffected = parts[1].trim().split(" ")[0];
				System.out.println("[Database] Cleaned up " + rowsAffected + " incomplete login session(s)");
			}
		} else {
			System.err.println("[Database] WARNING: Failed to cleanup incomplete sessions: " + result);
		}
	}

	public static Database getInstance() {
		return Instance.instance;
	}

	/**
	 * Execute SQL query and return result
	 * @param sql SQL query string
	 * @return Result string from SQL server
	 */
	private String executeSQL(String sql) {
		System.out.println("[Database] Executing SQL: " + sql);
		try (Socket socket = new Socket(sqlHost, sqlPort);
			 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
			 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
			
			// Send SQL with null terminator
			out.print(sql + '\0');
			out.flush();
			
			// Read response until null terminator
			StringBuilder response = new StringBuilder();
			int ch;
			while ((ch = in.read()) != -1 && ch != '\0') {
				response.append((char) ch);
			}
			
			String result = response.toString();
			System.out.println("[Database] SQL Response: " + (result.length() > 100 ? result.substring(0, 100) + "..." : result));
			return result;
			
		} catch (Exception e) {
			System.err.println("[Database] SQL Error: " + e.getMessage());
			return "ERROR:" + e.getMessage();
		}
	}

	/**
	 * Escape SQL special characters to prevent SQL injection
	 */
	private String escapeSql(String str) {
		if (str == null) return "";
		return str.replace("'", "''");
	}

	public void addUser(User user) {
		userMap.putIfAbsent(user.name, user);
		connectionsIdMap.putIfAbsent(user.getConnectionId(), user);
	}

	public LoginStatus login(int connectionId, String username, String password) {
		System.out.println("[Database] Login attempt - ConnectionID: " + connectionId + ", Username: " + username);
		
		if (connectionsIdMap.containsKey(connectionId)) {
			System.out.println("[Database] Login failed: CLIENT_ALREADY_CONNECTED");
			return LoginStatus.CLIENT_ALREADY_CONNECTED;
		}
		
		// Check if user exists in SQL database
		String checkUserSQL = String.format(
			"SELECT username, password FROM users WHERE username='%s'",
			escapeSql(username)
		);
		String result = executeSQL(checkUserSQL);
		
		if (result.startsWith("ERROR")) {
			System.err.println("[Database] CRITICAL: SQL error checking user: " + result);
		}
		
		String[] parts = result.split("\\|");
		boolean userExistsInSQL = parts.length > 1;
		
		if (!userExistsInSQL) {
			// New user - register in SQL
			System.out.println("[Database] New user - registering in SQL: " + username);
			String insertSQL = String.format(
				"INSERT INTO users (username, password, registration_date) VALUES ('%s', '%s', datetime('now'))",
				escapeSql(username), escapeSql(password)
			);
			String insertResult = executeSQL(insertSQL);
			
			if (insertResult.startsWith("ERROR")) {
				System.err.println("[Database] CRITICAL: Failed to register user in SQL: " + insertResult);
			}
			
			System.out.println("[Database] New user persisted to SQL database: " + username);
			
			// Add to in-memory map
			User user = new User(connectionId, username, password);
			user.login();
			addUser(user);
			
			// Log login
			logLogin(username);
			System.out.println("[Database] Login successful: ADDED_NEW_USER");
			return LoginStatus.ADDED_NEW_USER;
		} else {
			// User exists in SQL - verify password
			String[] userData = parts[1].split(",");
			String storedPassword = userData[1];
			
			if (!storedPassword.equals(password)) {
				System.out.println("[Database] Login failed: WRONG_PASSWORD");
				return LoginStatus.WRONG_PASSWORD;
			}
			
			// Check if user is already logged in via SQL
			String checkLoginSQL = String.format(
				"SELECT username FROM login_history WHERE username='%s' AND logout_time IS NULL",
				escapeSql(username)
			);
			String loginCheckResult = executeSQL(checkLoginSQL);
			String[] loginParts = loginCheckResult.split("\\|");
			
			if (loginParts.length > 1) {
				System.out.println("[Database] Login failed: ALREADY_LOGGED_IN");
				return LoginStatus.ALREADY_LOGGED_IN;
			}
			
			// Login successful - update in-memory map
			User user = userMap.get(username);
			if (user == null) {
				// User exists in SQL but not in memory (server restart)
				user = new User(connectionId, username, password);
				userMap.put(username, user);
				System.out.println("[Database] Loaded user from SQL into memory");
			}
			user.login();
			user.setConnectionId(connectionId);
			connectionsIdMap.put(connectionId, user);
			
			// Log login
			logLogin(username);
			System.out.println("[Database] Login successful: LOGGED_IN_SUCCESSFULLY");
			return LoginStatus.LOGGED_IN_SUCCESSFULLY;
		}
	}

	private void logLogin(String username) {
		String sql = String.format(
			"INSERT INTO login_history (username, login_time) VALUES ('%s', datetime('now'))",
			escapeSql(username)
		);
		executeSQL(sql);
	}

	// Deprecated - logic moved to login() method
	@Deprecated
	private LoginStatus userExistsCase(int connectionId, String username, String password) {
		// Check SQL for user data and login status
		String checkUserSQL = String.format(
			"SELECT username, password FROM users WHERE username='%s'",
			escapeSql(username)
		);
		String result = executeSQL(checkUserSQL);
		
		if (result.startsWith("ERROR")) {
			System.err.println("[Database] CRITICAL: SQL error checking user in userExistsCase: " + result);
			throw new RuntimeException("Database error during login verification");
		}
		
		String[] parts = result.split("\\|");
		if (parts.length <= 1) {
			// User not found in SQL - this is a critical logic error
			System.err.println("[Database] CRITICAL ERROR: User '" + username + "' not found in SQL database despite addNewUserCase returning false!");
			System.err.println("[Database] This indicates a race condition or database inconsistency.");
			throw new RuntimeException("Database inconsistency detected during login");
		}
		
		// Parse user data from SQL
		String[] userData = parts[1].split(",");
		String storedPassword = userData[1];
		
		// Check password
		if (!storedPassword.equals(password)) {
			System.out.println("[Database] Wrong password for user: " + username);
			return LoginStatus.WRONG_PASSWORD;
		}
		
		// Check if user is already logged in via SQL
		String checkLoginSQL = String.format(
			"SELECT username FROM login_history WHERE username='%s' AND logout_time IS NULL",
			escapeSql(username)
		);
		String loginCheckResult = executeSQL(checkLoginSQL);
		String[] loginParts = loginCheckResult.split("\\|");
		boolean alreadyLoggedIn = loginParts.length > 1;
		
		if (alreadyLoggedIn) {
			System.out.println("[Database] User already logged in: " + username);
			return LoginStatus.ALREADY_LOGGED_IN;
		}
		
		// Login successful - update in-memory map
		User user = userMap.get(username);
		if (user == null) {
			// Load from SQL into memory
			user = new User(connectionId, username, password);
			userMap.put(username, user);
			System.out.println("[Database] Loaded user from SQL into memory");
		}
		user.login();
		user.setConnectionId(connectionId);
		connectionsIdMap.put(connectionId, user);
		
		return LoginStatus.LOGGED_IN_SUCCESSFULLY;
	}

	@Deprecated
	private boolean addNewUserCase(int connectionId, String username, String password) {
		// Check SQL to see if user exists
		String checkUserSQL = String.format(
			"SELECT username FROM users WHERE username='%s'",
			escapeSql(username)
		);
		String result = executeSQL(checkUserSQL);
		String[] parts = result.split("\\|");
		boolean userExistsInSQL = parts.length > 1;
		
		if (!userExistsInSQL) {
			// User doesn't exist in SQL - add to both SQL and memory
			User user = new User(connectionId, username, password);
			user.login();
			addUser(user);
			return true;
		}
		return false;
	}

	public void logout(int connectionsId) {
		User user = connectionsIdMap.get(connectionsId);
		if (user != null) {
			System.out.println("[Database] Logging out user: " + user.name + " (ConnectionID: " + connectionsId + ")");
			// Log logout in SQL
			String sql = String.format(
				"UPDATE login_history SET logout_time=datetime('now') " +
				"WHERE username='%s' AND logout_time IS NULL " +
				"ORDER BY login_time DESC LIMIT 1",
				escapeSql(user.name)
			);
			executeSQL(sql);
			
			user.logout();
			connectionsIdMap.remove(connectionsId);
			System.out.println("[Database] User " + user.name + " logged out successfully");
		} else {
			System.out.println("[Database] WARNING: Logout attempted for unknown connectionId: " + connectionsId);
		}
	}

	/**
	 * Track file upload in SQL database
	 * @param username User who uploaded the file
	 * @param filename Name of the file
	 * @param gameChannel Game channel the file was reported to
	 */
	public void logFile(String username, String filename, String gameChannel) {
		System.out.println("[Database] Tracking file upload - User: " + username + ", File: " + filename + ", Channel: " + gameChannel);
		String sql = String.format(
			"INSERT INTO file_tracking (username, filename, upload_time, game_channel) " +
			"VALUES ('%s', '%s', datetime('now'), '%s')",
			escapeSql(username), escapeSql(filename), escapeSql(gameChannel)
		);
		String result = executeSQL(sql);
		if (result.startsWith("SUCCESS")) {
			System.out.println("[Database] File upload tracked successfully");
		} else {
			System.out.println("[Database] ERROR: Failed to track file upload - " + result);
		}
	}

	/**
	 * Generate and print server report using SQL queries
	 */
	public void printReport() {
		System.out.println(repeat("=", 80));
		System.out.println("SERVER REPORT - Generated at: " + java.time.LocalDateTime.now());
		System.out.println(repeat("=", 80));
		
		// List all users
		System.out.println("\n1. REGISTERED USERS:");
		System.out.println(repeat("-", 80));
		String usersSQL = "SELECT username, registration_date FROM users ORDER BY registration_date";
		String usersResult = executeSQL(usersSQL);
		if (usersResult.startsWith("SUCCESS")) {
			String[] rows = usersResult.split("\\|");
			if (rows.length > 1) {
				for (int i = 1; i < rows.length; i++) {
					String[] fields = rows[i].split(",");
					if (fields.length >= 2) {
						System.out.println("   Username: " + fields[0] + ", Registered: " + fields[1]);
					}
				}
			} else {
				System.out.println("   No users registered");
			}
		} else {
			System.out.println("   ERROR: " + usersResult);
		}
		
		// Login history for each user
		System.out.println("\n2. LOGIN HISTORY:");
		System.out.println(repeat("-", 80));
		String loginSQL = "SELECT username, login_time, logout_time FROM login_history ORDER BY username, login_time DESC";
		String loginResult = executeSQL(loginSQL);
		if (loginResult.startsWith("SUCCESS")) {
			String[] rows = loginResult.split("\\|");
			if (rows.length > 1) {
				String currentUser = "";
				for (int i = 1; i < rows.length; i++) {
					String[] fields = rows[i].split(",");
					if (fields.length >= 3) {
						String username = fields[0];
						String loginTime = fields[1];
						String logoutTime = fields[2].isEmpty() ? "Still logged in" : fields[2];
						
						if (!username.equals(currentUser)) {
							currentUser = username;
							System.out.println("\n   User: " + currentUser);
						}
						System.out.println("      Login:  " + loginTime);
						System.out.println("      Logout: " + logoutTime);
					}
				}
			} else {
				System.out.println("   No login history");
			}
		} else {
			System.out.println("   ERROR: " + loginResult);
		}
		
		// File uploads for each user
		System.out.println("\n3. FILE UPLOADS:");
		System.out.println(repeat("-", 80));
		String filesSQL = "SELECT username, filename, upload_time, game_channel FROM file_tracking ORDER BY username, upload_time DESC";
		String filesResult = executeSQL(filesSQL);
		if (filesResult.startsWith("SUCCESS")) {
			String[] rows = filesResult.split("\\|");
			if (rows.length > 1) {
				String currentUser = "";
				for (int i = 1; i < rows.length; i++) {
					String[] fields = rows[i].split(",");
					if (fields.length >= 4) {
						String username = fields[0];
						String filename = fields[1];
						String uploadTime = fields[2];
						String gameChannel = fields[3];
						
						if (!username.equals(currentUser)) {
							currentUser = username;
							System.out.println("\n   User: " + currentUser);
						}
						System.out.println("      File: " + filename);
						System.out.println("      Time: " + uploadTime);
						System.out.println("      Game: " + gameChannel);
						System.out.println();
					}
				}
			} else {
				System.out.println("   No files uploaded");
			}
		} else {
			System.out.println("   ERROR: " + filesResult);
		}
		
		System.out.println(repeat("=", 80));
	}

private String repeat(String str, int times) {
	StringBuilder sb = new StringBuilder();
	for (int i = 0; i < times; i++) {
		sb.append(str);
	}
	return sb.toString();
}

private static class Instance {
	static Database instance = new Database();
}}