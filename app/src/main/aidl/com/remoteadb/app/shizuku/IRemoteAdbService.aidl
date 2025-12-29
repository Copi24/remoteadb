package com.remoteadb.app.shizuku;

/**
 * AIDL interface for RemoteADB UserService.
 * This service runs with shell (ADB) privileges via Shizuku.
 */
interface IRemoteAdbService {
    // Shizuku reserved destroy method
    void destroy() = 16777114;
    
    // Exit the service
    void exit() = 1;
    
    // Execute a shell command and return stdout
    String execCommand(String command) = 2;
    
    // Execute a command and return exit code
    int execCommandWithExitCode(String command) = 3;
    
    // Read a file and return its contents as bytes
    byte[] readFile(String path) = 4;
    
    // Write bytes to a file
    boolean writeFile(String path, in byte[] data) = 5;
    
    // List directory contents
    String[] listDirectory(String path) = 6;
    
    // Check if file/directory exists
    boolean exists(String path) = 7;
    
    // Delete file or directory
    boolean delete(String path) = 8;
    
    // Get process ID (for verification)
    int getPid() = 9;
    
    // Get UID (for verification - should be 2000 for shell)
    int getUid() = 10;
}
