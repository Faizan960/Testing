#include <Arduino.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLEAdvertising.h>

static const char* DEVICE_NAME = "NIYYAH-MASJID-TEST";
static const char* SERVICE_UUID = "12345678-1234-1234-1234-1234567890AB";

void setup() {
  Serial.begin(115200);
  delay(500);

  Serial.println();
  Serial.println("==============================");
  Serial.println("NIYYAH MASJID NODE");
  Serial.println("BLE BEACON STARTING");
  Serial.println("==============================");

  BLEDevice::init(DEVICE_NAME);

  BLEServer* server = BLEDevice::createServer();
  BLEService* service = server->createService(SERVICE_UUID);
  service->start();

  BLEAdvertising* advertising = BLEDevice::getAdvertising();
  advertising->addServiceUUID(SERVICE_UUID);
  advertising->setScanResponse(true);
  advertising->start();

  Serial.print("BLE device: ");
  Serial.println(DEVICE_NAME);
  Serial.print("Service UUID: ");
  Serial.println(SERVICE_UUID);
  Serial.println("Advertising started.");
}

void loop() {
  Serial.println("Beacon advertising...");
  delay(5000);
}
