package com.imagelab.data;

public class DogApiResponse {
    private String message;
    private String status;

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isSuccess() { return "success".equalsIgnoreCase(status); }

    @Override
    public String toString() {
        return "DogApiResponse{status='" + status + "', url='" + message + "'}";
    }
}