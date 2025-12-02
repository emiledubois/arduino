
package com.arduino

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.arduino.bluetooth.BluetoothManager
import com.arduino.network.RetrofitClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
        private const val THINGSPEAK_API_KEY = "\n" + "NMB3DR530HQ8CYDQ\n"
        private const val COMANDO_LEER_SENSORES = "READ\n"
        private const val INTERVALO_LECTURA = 5000L
    }

    private val bluetoothManager = BluetoothManager(application)

    private val _estadoConexion = MutableLiveData<String>()
    val estadoConexion: LiveData<String> = _estadoConexion

    private val _dispositivosEmparejados = MutableLiveData<List<BluetoothDevice>>()
    val dispositivosEmparejados: LiveData<List<BluetoothDevice>> = _dispositivosEmparejados

    private val _sensor1Value = MutableLiveData<String>()
    val sensor1Value: LiveData<String> = _sensor1Value

    private val _sensor2Value = MutableLiveData<String>()
    val sensor2Value: LiveData<String> = _sensor2Value

    private val _ultimoEnvioThingSpeak = MutableLiveData<String>()
    val ultimoEnvioThingSpeak: LiveData<String> = _ultimoEnvioThingSpeak

    private val _lecturaAutomaticaActiva = MutableLiveData(false)
    val lecturaAutomaticaActiva: LiveData<Boolean> = _lecturaAutomaticaActiva

    init {
        _estadoConexion.value = "Desconectado"
        cargarDispositivosEmparejados()
    }

    fun cargarDispositivosEmparejados() {
        viewModelScope.launch {
            if (!bluetoothManager.isBluetoothAvailable()) {
                _estadoConexion.value = "Bluetooth no disponible"
                return@launch
            }

            if (!bluetoothManager.isBluetoothEnabled()) {
                _estadoConexion.value = "Bluetooth desactivado"
                return@launch
            }

            val dispositivos = bluetoothManager.getPairedDevices()
            _dispositivosEmparejados.value = dispositivos

            if (dispositivos.isEmpty()) {
                _estadoConexion.value = "No hay dispositivos emparejados"
            }
        }
    }

    fun conectarDispositivo(device: BluetoothDevice) {
        viewModelScope.launch {
            _estadoConexion.value = "Conectando..."

            val exito = bluetoothManager.conectarDispositivo(device)

            if (exito) {
                _estadoConexion.value = "Conectado a ${device.name}"
            } else {
                _estadoConexion.value = "Error al conectar"
            }
        }
    }

    fun desconectar() {
        viewModelScope.launch {
            detenerLecturaAutomatica()
            bluetoothManager.desconectar()
            _estadoConexion.value = "Desconectado"
            _sensor1Value.value = "---"
            _sensor2Value.value = "---"
        }
    }

    fun leerSensores() {
        viewModelScope.launch {
            if (!bluetoothManager.estaConectado()) {
                _estadoConexion.value = "No hay conexión"
                return@launch
            }

            bluetoothManager.enviarComando(COMANDO_LEER_SENSORES)
            delay(500)

            val datos = bluetoothManager.leerDatos()

            if (datos != null) {
                procesarDatosSensores(datos)
            } else {
                Log.w(TAG, "No se recibieron datos")
            }
        }
    }

    private fun procesarDatosSensores(datos: String) {
        try {
            val partes = datos.split(",")

            if (partes.size >= 2) {
                val valor1 = partes[0].substringAfter(":").trim()
                val valor2 = partes[1].substringAfter(":").trim()

                _sensor1Value.value = valor1
                _sensor2Value.value = valor2

                Log.d(TAG, "Sensor 1: $valor1, Sensor 2: $valor2")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al procesar datos: ${e.message}")
        }
    }

    fun enviarDatosThingSpeak() {
        viewModelScope.launch {
            val valor1 = _sensor1Value.value ?: return@launch
            val valor2 = _sensor2Value.value ?: return@launch

            if (valor1 == "---" || valor2 == "---") {
                _ultimoEnvioThingSpeak.value = "Sin datos para enviar"
                return@launch
            }

            try {
                _ultimoEnvioThingSpeak.value = "Enviando..."

                val response = RetrofitClient.thingSpeakService.enviarDatos(
                    apiKey = THINGSPEAK_API_KEY,
                    field1 = valor1,
                    field2 = valor2
                )

                if (response.isSuccessful) {
                    _ultimoEnvioThingSpeak.value = "Enviado exitosamente"
                    Log.i(TAG, "Datos enviados a ThingSpeak")
                } else {
                    _ultimoEnvioThingSpeak.value = "Error: ${response.code()}"
                    Log.e(TAG, "Error: ${response.code()}")
                }

            } catch (e: Exception) {
                _ultimoEnvioThingSpeak.value = "Error: ${e.message}"
                Log.e(TAG, "Error al enviar: ${e.message}")
            }
        }
    }

    fun iniciarLecturaAutomatica() {
        if (_lecturaAutomaticaActiva.value == true) return

        _lecturaAutomaticaActiva.value = true

        viewModelScope.launch {
            while (_lecturaAutomaticaActiva.value == true) {
                leerSensores()
                delay(INTERVALO_LECTURA)
                enviarDatosThingSpeak()
            }
        }
    }

    fun detenerLecturaAutomatica() {
        _lecturaAutomaticaActiva.value = false
    }

    override fun onCleared() {
        super.onCleared()
        bluetoothManager.desconectar()
    }
}