# Pull Request: Add MockRemoteProcess class

## Description
Fixes compilation error `error: cannot find symbol class MockRemoteProcess` in `ShizukuService.java:580`

## Changes
- Added new `MockRemoteProcess` class that implements `IRemoteProcess` interface
- Provides mock implementation for process operations with configurable exit codes and error messages
- Used by ShizukuService to safely block catastrophic commands without crashing

## Issue
The build was failing because `ShizukuService.newProcess()` was attempting to instantiate `MockRemoteProcess` class at line 580, but the class didn't exist.

## Solution
Created `MockRemoteProcess.java` with a proper implementation of the `IRemoteProcess.Stub` interface that:
- Returns mock input/error streams
- Provides configurable exit codes
- Handles the destroy operation safely

This allows the Catastrophic Command Interceptor feature in ShizukuService to safely deny dangerous operations like `mkfs`, `rm -rf /system`, etc.
