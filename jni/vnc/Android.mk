LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_CFLAGS +=  -Wall


LOCAL_SRC_FILES:= \
	fbvncserver.c \
	suinput.c \
	LibVNCServer-0.9.8.2/libvncserver/main.c \
	LibVNCServer-0.9.8.2/libvncserver/rfbserver.c \
	LibVNCServer-0.9.8.2/libvncserver/rfbregion.c \
	LibVNCServer-0.9.8.2/libvncserver/auth.c \
	LibVNCServer-0.9.8.2/libvncserver/sockets.c \
	LibVNCServer-0.9.8.2/libvncserver/stats.c \
	LibVNCServer-0.9.8.2/libvncserver/corre.c \
	LibVNCServer-0.9.8.2/libvncserver/hextile.c \
	LibVNCServer-0.9.8.2/libvncserver/rre.c \
	LibVNCServer-0.9.8.2/libvncserver/translate.c \
	LibVNCServer-0.9.8.2/libvncserver/cutpaste.c \
	LibVNCServer-0.9.8.2/libvncserver/httpd.c \
	LibVNCServer-0.9.8.2/libvncserver/cursor.c \
	LibVNCServer-0.9.8.2/libvncserver/font.c \
	LibVNCServer-0.9.8.2/libvncserver/draw.c \
	LibVNCServer-0.9.8.2/libvncserver/selbox.c \
	LibVNCServer-0.9.8.2/libvncserver/d3des.c \
	LibVNCServer-0.9.8.2/libvncserver/vncauth.c \
	LibVNCServer-0.9.8.2/libvncserver/cargs.c \
	LibVNCServer-0.9.8.2/libvncserver/minilzo.c \
	LibVNCServer-0.9.8.2/libvncserver/ultra.c \
	LibVNCServer-0.9.8.2/libvncserver/scale.c \
	LibVNCServer-0.9.8.2/libvncserver/zlib.c \
	LibVNCServer-0.9.8.2/libvncserver/zrle.c \
	LibVNCServer-0.9.8.2/libvncserver/zrleoutstream.c \
	LibVNCServer-0.9.8.2/libvncserver/zrlepalettehelper.c \
	LibVNCServer-0.9.8.2/libvncserver/zywrletemplate.c \
	LibVNCServer-0.9.8.2/libvncserver/tight.c

LOCAL_C_INCLUDES := \
	$(LOCAL_PATH) \
	$(LOCAL_PATH)/LibVNCServer-0.9.8.2/libvncserver \
	$(LOCAL_PATH)/LibVNCServer-0.9.8.2 \
	$(LOCAL_PATH)/../jpeg


LOCAL_STATIC_LIBRARIES := jpeg

LOCAL_MODULE:= androidvncserver

LOCAL_LDLIBS := -lz -llog


include $(BUILD_EXECUTABLE)
