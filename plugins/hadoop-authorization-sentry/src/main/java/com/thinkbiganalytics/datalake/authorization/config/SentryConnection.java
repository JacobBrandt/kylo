package com.thinkbiganalytics.datalake.authorization.config;

import com.thinkbiganalytics.datalake.authorization.AuthorizationConfiguration;

/**
 * Created by Shashi Vishwakarma on 20/9/16.
 */

public class SentryConnection implements AuthorizationConfiguration {

    private String connectionURL;
    private String driverName;
    private String username;
    private String password;

      public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

      public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

	public String getConnectionURL() {
		return connectionURL;
	}

	public void setConnectionURL(String connectionURL) {
		this.connectionURL = connectionURL;
	}

	public String getDriverName() {
		return driverName;
	}

	public void setDriverName(String driverName) {
		this.driverName = driverName;
	}

}