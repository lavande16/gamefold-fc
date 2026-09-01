#include <jni.h>
#include <android/log.h>

#include <atomic>
#include <algorithm>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <mutex>
#include <string>
#include <vector>

#include "libretro.h"

#ifndef RETRO_ENVIRONMENT_GET_TARGET_SAMPLE_RATE
#define RETRO_ENVIRONMENT_GET_TARGET_SAMPLE_RATE (81 | RETRO_ENVIRONMENT_EXPERIMENTAL)
#endif

namespace {

constexpr const char *LogTag = "GameFoldEmu";
constexpr int MaxFramePixels = 512 * 512;

std::mutex g_coreMutex;
std::atomic<uint32_t> g_inputMask{0};
std::vector<uint32_t> g_frame(MaxFramePixels, 0xFF000000u);
std::vector<int16_t> g_audio;
std::string g_systemDir;
std::string g_saveDir;
unsigned g_frameWidth = 0;
unsigned g_frameHeight = 0;
double g_sampleRate = 48000.0;
double g_frameRate = 60.0;
bool g_initialized = false;
bool g_loaded = false;
retro_pixel_format g_pixelFormat = RETRO_PIXEL_FORMAT_RGB565;

void logInfo(const char *message) {
    __android_log_write(ANDROID_LOG_INFO, LogTag, message);
}

bool environmentCallback(unsigned command, void *data) {
    switch (command) {
        case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT: {
            const auto requested = *static_cast<const retro_pixel_format *>(data);
            if (requested == RETRO_PIXEL_FORMAT_RGB565 ||
                requested == RETRO_PIXEL_FORMAT_XRGB8888) {
                g_pixelFormat = requested;
                return true;
            }
            return false;
        }
        case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY:
            *static_cast<const char **>(data) = g_systemDir.c_str();
            return true;
        case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY:
            *static_cast<const char **>(data) = g_saveDir.c_str();
            return true;
        case RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE:
            *static_cast<bool *>(data) = false;
            return true;
        case RETRO_ENVIRONMENT_GET_INPUT_BITMASKS:
            return true;
        case RETRO_ENVIRONMENT_GET_TARGET_SAMPLE_RATE:
            *static_cast<double *>(data) = 48000.0;
            return true;
        case RETRO_ENVIRONMENT_SET_SYSTEM_AV_INFO: {
            const auto *info = static_cast<const retro_system_av_info *>(data);
            if (info) {
                g_frameWidth = static_cast<unsigned>(info->geometry.base_width);
                g_frameHeight = static_cast<unsigned>(info->geometry.base_height);
                if (info->timing.sample_rate > 0.0) {
                    g_sampleRate = info->timing.sample_rate;
                }
                if (info->timing.fps > 0.0) {
                    g_frameRate = info->timing.fps;
                }
            }
            return true;
        }
        case RETRO_ENVIRONMENT_SET_GEOMETRY: {
            const auto *geometry = static_cast<const retro_game_geometry *>(data);
            if (geometry) {
                g_frameWidth = geometry->base_width;
                g_frameHeight = geometry->base_height;
            }
            return true;
        }
        case RETRO_ENVIRONMENT_SET_PERFORMANCE_LEVEL:
        case RETRO_ENVIRONMENT_SET_CONTROLLER_INFO:
        case RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS:
        case RETRO_ENVIRONMENT_SET_CONTENT_INFO_OVERRIDE:
        case RETRO_ENVIRONMENT_SET_SUPPORT_ACHIEVEMENTS:
        case RETRO_ENVIRONMENT_SET_MESSAGE:
        case RETRO_ENVIRONMENT_SET_MESSAGE_EXT:
        case RETRO_ENVIRONMENT_SET_MEMORY_MAPS:
            return true;
        default:
            return false;
    }
}

void videoCallback(const void *data, unsigned width, unsigned height, size_t pitch) {
    if (!data || width == 0 || height == 0 || width * height > g_frame.size()) {
        return;
    }

    g_frameWidth = width;
    g_frameHeight = height;

    if (g_pixelFormat == RETRO_PIXEL_FORMAT_RGB565) {
        const auto *src = static_cast<const uint8_t *>(data);
        for (unsigned y = 0; y < height; y++) {
            const auto *row = reinterpret_cast<const uint16_t *>(src + y * pitch);
            for (unsigned x = 0; x < width; x++) {
                const uint16_t p = row[x];
                const uint32_t r = ((p >> 11) & 0x1F) << 3;
                const uint32_t g = ((p >> 5) & 0x3F) << 2;
                const uint32_t b = (p & 0x1F) << 3;
                g_frame[y * width + x] = 0xFF000000u | (r << 16) | (g << 8) | b;
            }
        }
        return;
    }

    if (g_pixelFormat == RETRO_PIXEL_FORMAT_XRGB8888) {
        const auto *src = static_cast<const uint8_t *>(data);
        for (unsigned y = 0; y < height; y++) {
            const auto *row = reinterpret_cast<const uint32_t *>(src + y * pitch);
            for (unsigned x = 0; x < width; x++) {
                g_frame[y * width + x] = 0xFF000000u | (row[x] & 0x00FFFFFFu);
            }
        }
    }
}

void audioSampleCallback(int16_t left, int16_t right) {
    g_audio.push_back(left);
    g_audio.push_back(right);
}

size_t audioBatchCallback(const int16_t *data, size_t frames) {
    if (data && frames > 0) {
        const size_t sampleCount = frames * 2;
        g_audio.insert(g_audio.end(), data, data + sampleCount);
    }
    return frames;
}

void inputPollCallback() {}

int16_t inputStateCallback(unsigned port, unsigned device, unsigned, unsigned id) {
    if (port != 0 || device != RETRO_DEVICE_JOYPAD) {
        return 0;
    }

    const uint32_t mask = g_inputMask.load(std::memory_order_relaxed);
    if (id == RETRO_DEVICE_ID_JOYPAD_MASK) {
        return static_cast<int16_t>(mask);
    }
    if (id < 16 && (mask & (1u << id))) {
        return 1;
    }
    return 0;
}

std::string jStringToString(JNIEnv *env, jstring value) {
    if (!value) {
        return {};
    }
    const char *chars = env->GetStringUTFChars(value, nullptr);
    std::string result(chars ? chars : "");
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

void ensureInitialized() {
    if (g_initialized) {
        return;
    }

    retro_set_environment(environmentCallback);
    retro_set_video_refresh(videoCallback);
    retro_set_audio_sample(audioSampleCallback);
    retro_set_audio_sample_batch(audioBatchCallback);
    retro_set_input_poll(inputPollCallback);
    retro_set_input_state(inputStateCallback);
    retro_init();
    retro_set_controller_port_device(0, RETRO_DEVICE_JOYPAD);
    g_initialized = true;
    logInfo("FCEUmm core initialized");
}

}  // namespace

extern "C" JNIEXPORT void JNICALL
Java_com_gamefold_gamefold_emulation_NativeNesEmulator_nativeInitialize(
    JNIEnv *env,
    jobject,
    jstring systemDir,
    jstring saveDir
) {
    std::lock_guard<std::mutex> lock(g_coreMutex);
    g_systemDir = jStringToString(env, systemDir);
    g_saveDir = jStringToString(env, saveDir);
    ensureInitialized();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_gamefold_gamefold_emulation_NativeNesEmulator_nativeLoadRom(
    JNIEnv *env,
    jobject,
    jstring romPath
) {
    std::lock_guard<std::mutex> lock(g_coreMutex);
    ensureInitialized();

    if (g_loaded) {
        retro_unload_game();
        g_loaded = false;
    }

    const std::string path = jStringToString(env, romPath);
    retro_game_info info{};
    info.path = path.c_str();
    info.data = nullptr;
    info.size = 0;
    info.meta = nullptr;

    g_frameWidth = 0;
    g_frameHeight = 0;
    g_audio.clear();
    g_loaded = retro_load_game(&info);
    if (g_loaded) {
        retro_system_av_info avInfo{};
        retro_get_system_av_info(&avInfo);
        g_frameWidth = avInfo.geometry.base_width;
        g_frameHeight = avInfo.geometry.base_height;
        if (avInfo.timing.sample_rate > 0.0) {
            g_sampleRate = avInfo.timing.sample_rate;
        }
        if (avInfo.timing.fps > 0.0) {
            g_frameRate = avInfo.timing.fps;
        }
    }
    retro_set_controller_port_device(0, RETRO_DEVICE_JOYPAD);
    return g_loaded ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_gamefold_gamefold_emulation_NativeNesEmulator_nativeRunFrame(
    JNIEnv *env,
    jobject,
    jintArray outPixels
) {
    std::lock_guard<std::mutex> lock(g_coreMutex);
    if (!g_initialized || !g_loaded || !outPixels) {
        return 0;
    }

    retro_run();

    const int pixelCount = static_cast<int>(g_frameWidth * g_frameHeight);
    if (pixelCount <= 0 || pixelCount > env->GetArrayLength(outPixels)) {
        return 0;
    }

    env->SetIntArrayRegion(
        outPixels,
        0,
        pixelCount,
        reinterpret_cast<const jint *>(g_frame.data())
    );
    return static_cast<jint>((g_frameWidth << 16u) | g_frameHeight);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_gamefold_gamefold_emulation_NativeNesEmulator_nativeDrainAudio(
    JNIEnv *env,
    jobject,
    jshortArray outSamples
) {
    std::lock_guard<std::mutex> lock(g_coreMutex);
    if (!outSamples || g_audio.empty()) {
        return 0;
    }

    const int capacity = env->GetArrayLength(outSamples);
    const int sampleCount = static_cast<int>(std::min(g_audio.size(), static_cast<size_t>(capacity)));
    env->SetShortArrayRegion(
        outSamples,
        0,
        sampleCount,
        reinterpret_cast<const jshort *>(g_audio.data())
    );
    g_audio.erase(g_audio.begin(), g_audio.begin() + sampleCount);
    return sampleCount;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_gamefold_gamefold_emulation_NativeNesEmulator_nativeGetSampleRate(
    JNIEnv *,
    jobject
) {
    return static_cast<jint>(g_sampleRate);
}

extern "C" JNIEXPORT jdouble JNICALL
Java_com_gamefold_gamefold_emulation_NativeNesEmulator_nativeGetFrameRate(
    JNIEnv *,
    jobject
) {
    return static_cast<jdouble>(g_frameRate);
}

extern "C" JNIEXPORT void JNICALL
Java_com_gamefold_gamefold_emulation_NativeNesEmulator_nativeSetInputMask(
    JNIEnv *,
    jobject,
    jint mask
) {
    g_inputMask.store(static_cast<uint32_t>(mask), std::memory_order_relaxed);
}

extern "C" JNIEXPORT void JNICALL
Java_com_gamefold_gamefold_emulation_NativeNesEmulator_nativeUnload(
    JNIEnv *,
    jobject
) {
    std::lock_guard<std::mutex> lock(g_coreMutex);
    if (g_initialized && g_loaded) {
        retro_unload_game();
        g_loaded = false;
        g_audio.clear();
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_gamefold_gamefold_emulation_NativeNesEmulator_nativeSaveState(
    JNIEnv *env,
    jobject,
    jstring statePath
) {
    std::lock_guard<std::mutex> lock(g_coreMutex);
    if (!g_initialized || !g_loaded) {
        return JNI_FALSE;
    }

    const size_t size = retro_serialize_size();
    if (size == 0) {
        return JNI_FALSE;
    }

    std::vector<uint8_t> data(size);
    if (!retro_serialize(data.data(), size)) {
        return JNI_FALSE;
    }

    const std::string path = jStringToString(env, statePath);
    FILE *file = std::fopen(path.c_str(), "wb");
    if (!file) {
        return JNI_FALSE;
    }
    const size_t written = std::fwrite(data.data(), 1, data.size(), file);
    std::fclose(file);
    return written == data.size() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_gamefold_gamefold_emulation_NativeNesEmulator_nativeLoadState(
    JNIEnv *env,
    jobject,
    jstring statePath
) {
    std::lock_guard<std::mutex> lock(g_coreMutex);
    if (!g_initialized || !g_loaded) {
        return JNI_FALSE;
    }

    const std::string path = jStringToString(env, statePath);
    FILE *file = std::fopen(path.c_str(), "rb");
    if (!file) {
        return JNI_FALSE;
    }
    std::fseek(file, 0, SEEK_END);
    const long length = std::ftell(file);
    std::rewind(file);
    if (length <= 0) {
        std::fclose(file);
        return JNI_FALSE;
    }

    std::vector<uint8_t> data(static_cast<size_t>(length));
    const size_t read = std::fread(data.data(), 1, data.size(), file);
    std::fclose(file);
    if (read != data.size()) {
        return JNI_FALSE;
    }
    return retro_unserialize(data.data(), data.size()) ? JNI_TRUE : JNI_FALSE;
}
