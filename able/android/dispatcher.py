from android import activity
from android.permissions import (
    Permission,
    check_permission,
    request_permission,
)
from jnius import autoclass
from kivy.logger import Logger

from able.android.jni import PythonBluetooth
from able.dispatcher import BluetoothDispatcherBase

import traceback


Activity = autoclass('android.app.Activity')
BLE = autoclass('org.able.BLE')

BluetoothGattDescriptor = autoclass(
    'android.bluetooth.BluetoothGattDescriptor')
ENABLE_NOTIFICATION_VALUE = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
DISABLE_NOTIFICATION_VALUE = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE


class BluetoothDispatcher(BluetoothDispatcherBase):

    @staticmethod
    def get_scan_settings(scan_mode=None, match_mode=None, report_delay=None,
                          num_of_matches=None, callback_type=None):
        try:
            Builder = autoclass("android.bluetooth.le.ScanSettings$Builder")
            b = Builder()
            if scan_mode is not None:
                b.setScanMode(scan_mode)
            if match_mode is not None:
                b.setMatchMode(match_mode)
            if report_delay is not None:
                b.setReportDelay(report_delay)
            if num_of_matches is not None:
                b.setNumOfMatches(num_of_matches)
            if callback_type is not None:
                b.setCallbackType(callback_type)
            return b.build()
        except Exception:
            Logger.error("Dispatcher : " + traceback.format_exc())
            return None

    @staticmethod
    def get_scan_filter(
            deviceAddress=None,
            deviceName=None,
            manufacturerId=None,
            manufacturerData=None,
            manufacturerDataMask=None,
            serviceDataUuid=None,
            serviceData=None,
            serviceDataMask=None,
            serviceSolicitationUuid=None,
            solicitationUuidMask=None,
            serviceUuid=None,
            uuidMask=None):
        try:
            ParcelUuid = autoclass('android.os.ParcelUuid')
            Builder = autoclass("android.bluetooth.le.ScanFilter$Builder")
            b = Builder()
            u = ParcelUuid.fromString
            if deviceAddress is not None:
                b.setDeviceAddress(deviceAddress)
            if deviceName is not None:
                b.setDeviceName(deviceName)
            if manufacturerId is not None and manufacturerData is not None and manufacturerDataMask is not None:
                b.setManufacturerData(manufacturerId, manufacturerData, manufacturerDataMask)
            elif manufacturerId is not None and manufacturerData is not None:
                b.setManufacturerData(manufacturerId, manufacturerData)
            if serviceDataUuid is not None and serviceData is not None:
                b.setServiceData(u(serviceDataUuid), serviceData)
            elif serviceDataUuid is not None and serviceData is not None and serviceDataMask is not None:
                b.setServiceData(u(serviceDataUuid), serviceData, serviceDataMask)
            if serviceSolicitationUuid is not None and solicitationUuidMask is not None:
                b.setServiceSolicitationUuid(u(serviceSolicitationUuid), u(solicitationUuidMask))
            elif serviceSolicitationUuid is not None:
                b.setServiceSolicitationUuid(u(serviceSolicitationUuid))
            if serviceUuid is not None and uuidMask is not None:
                b.setServiceUuid(u(serviceUuid), u(uuidMask))
            elif serviceUuid is not None:
                b.setServiceUuid(u(serviceUuid))
            return b.build()
        except Exception:
            Logger.error("Dispatcher : " + traceback.format_exc())
            return None

    @staticmethod
    def pack_filters(filts):
        List = autoclass('java.util.ArrayList')
        ll = List()
        for f in filts:
            if isinstance(f, dict):
                f = BluetoothDispatcher.get_scan_filter(**f)
            ll.add(f)
        return ll

    def _set_ble_interface(self):
        self._events_interface = PythonBluetooth(self)
        self._ble = BLE(self._events_interface)

        if activity:
            activity.bind(on_activity_result=self.on_activity_result)

    def convert_scan_settings(self, scan_settings):
        if scan_settings:
            if isinstance(scan_settings, dict):
                scan_settings = BluetoothDispatcher.get_scan_settings(**scan_settings)
        return scan_settings

    def convert_scan_filters(self, scan_filters):
        if scan_filters:
            if isinstance(scan_filters, (list, tuple)):
                scan_filters = BluetoothDispatcher.pack_filters(scan_filters)
        return scan_filters

    def _check_runtime_permissions(self):
        # Either ACCESS_COARSE_LOCATION or ACCESS_FINE_LOCATION permission
        # is needed to obtain BLE scan results
        return check_permission(Permission.ACCESS_FINE_LOCATION) if activity else True

    def _request_runtime_permissions(self):
        if activity:
            request_permission(Permission.ACCESS_FINE_LOCATION, self.on_runtime_permissions)
        else:
            self.on_runtime_permissions(None, None)

    def enable_notifications(self, characteristic, enable=True):
        if not self.gatt.setCharacteristicNotification(characteristic, enable):
            return False
        descriptor_value = (ENABLE_NOTIFICATION_VALUE if enable
                            else DISABLE_NOTIFICATION_VALUE)
        for descriptor in characteristic.getDescriptors().toArray():
            self.write_descriptor(descriptor, descriptor_value)
        return True

    def on_runtime_permissions(self, permissions, grant_results):
        if permissions and all(grant_results):
            self.start_scan(self.scan_settings, self.scan_filters)
        else:
            Logger.error(
                'Permissions necessary to obtain scan results are not granted'
            )
            self.dispatch('on_scan_started', False)

    def on_activity_result(self, requestCode, resultCode, intent):
        if requestCode == self.enable_ble_code:
            self.on_bluetooth_enabled(resultCode == Activity.RESULT_OK)

    def on_bluetooth_enabled(self, enabled):
        if enabled:
            self.start_scan(self.scan_settings, self.scan_filters)
        else:
            self.dispatch('on_scan_started', False)
