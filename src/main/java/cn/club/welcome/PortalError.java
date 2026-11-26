package cn.club.welcome;

public class PortalError extends RuntimeException {
    final int status;
    public PortalError(int status,String message) { super(message);this.status=status; }
}
