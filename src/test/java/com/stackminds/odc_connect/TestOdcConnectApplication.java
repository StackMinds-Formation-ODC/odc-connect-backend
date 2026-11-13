package com.stackminds.odc_connect;

import org.springframework.boot.SpringApplication;

public class TestOdcConnectApplication {

	public static void main(String[] args) {
		SpringApplication.from(OdcConnectApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
