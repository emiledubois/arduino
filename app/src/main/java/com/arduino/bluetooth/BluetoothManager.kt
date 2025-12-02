package com.arduino.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class BluetoothManager(private val context: Context) {

    companion object {
        private const val TAG = "BluetoothManager"
        private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    fun isBluetoothAvailable(): Boolean {
        return bluetoothAdapter != null
    }

    @SuppressLint("MissingPermission")
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> {
        if (!checkBluetoothPermissions()) {
            return emptyList()
        }

        return bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
    }

    @SuppressLint("MissingPermission")
    suspend fun conectarDispositivo(device: BluetoothDevice): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!checkBluetoothPermissions()) {
                Log.e(TAG, "Permisos Bluetooth no concedidos")
                return@withContext false
            }

            bluetoothAdapter?.cancelDiscovery()

            bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID)
            bluetoothSocket?.connect()

            inputStream = bluetoothSocket?.inputStream
            outputStream = bluetoothSocket?.outputStream

            Log.i(TAG, "Conectado a ${device.name}")
            true

        } catch (e: IOException) {
            Log.e(TAG, "Error al conectar: ${e.message}")
            desconectar()
            false
        }
    }

    suspend fun leerDatos(): String? = withContext(Dispatchers.IO) {
        try {
            val buffer = ByteArray(1024)
            val bytes = inputStream?.read(buffer)

            if (bytes != null && bytes > 0) {
                val datos = String(buffer, 0, bytes).trim()
                Log.d(TAG, "Datos recibidos: $datos")
                datos
            } else {
                null
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error al leer datos: ${e.message}")
            null
        }
    }

    suspend fun enviarComando(comando: String): Boolean = withContext(Dispatchers.IO) {
        try {
            outputStream?.write(comando.toByteArray())
            outputStream?.flush()
            Log.d(TAG, "Comando enviado: $comando")
            true
        } catch (e: IOException) {
            Log.e(TAG, "Error al enviar comando: ${e.message}")
            false
        }
    }

    fun desconectar() {
        try {
            inputStream?.close()
            outputStream?.close()
            bluetoothSocket?.close()

            inputStream = null
            outputStream = null
            bluetoothSocket = null

            Log.i(TAG, "Desconectado")
        } catch (e: IOException) {
            Log.e(TAG, "Error al desconectar: ${e.message}")
        }
    }

    fun estaConectado(): Boolean {
        return bluetoothSocket?.isConnected == true
    }

    private fun checkBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}