from able.structures import Advertisement, Services
from able.version import __version__  # noqa
from kivy.utils import platform

__all__ = ('Advertisement',
           'BluetoothDispatcher',
           'Services',)

# constants
GATT_SUCCESS = 0  #: GATT operation completed successfully
STATE_CONNECTED = 2  #: The profile is in connected state
STATE_DISCONNECTED = 0  #: The profile is in disconnected state

REASON_NO_BLE = 1
REASON_NO_ADAPTER = 2
REASON_NOT_ENABLED = 3
REASON_DISCOVER_ERROR = 4
REASON_NO_SCANNER = 5

if platform == 'android':
    from able.android.dispatcher import BluetoothDispatcher
else:
    from able.dispatcher import BluetoothDispatcherBase

    class BluetoothDispatcher(BluetoothDispatcherBase):
        """Bluetooth Low Energy interface

        :param queue_timeout: BLE operations queue timeout
        :param enable_ble_code: request code to identify activity that alows
               user to turn on :func:`Bluetooth <start_scan>`
        """
