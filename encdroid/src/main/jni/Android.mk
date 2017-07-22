LOCAL_PATH := $(call my-dir)
my_LOCAL_PATH := $(LOCAL_PATH)

include $(call all-subdir-makefiles)
include $(CLEAR_VARS)

LOCAL_PATH := $(my_LOCAL_PATH)
LOCAL_MODULE    := pbkdf2
LOCAL_SRC_FILES := pbkdf2.c
LOCAL_STATIC_LIBRARIES := openssl
LOCAL_LDLIBS = -lz

include $(BUILD_SHARED_LIBRARY)
