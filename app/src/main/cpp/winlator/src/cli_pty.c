#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <termios.h>

/*
 * Allocate a pseudo-terminal pair and return the master file descriptor.
 * The slave path can be retrieved with getSlavePath(masterFd).
 * Returns -1 on failure.
 */
JNIEXPORT jint JNICALL
Java_com_winlator_cli_CliPty_openPty(JNIEnv* env, jclass clazz, jint cols, jint rows) {
    int master = posix_openpt(O_RDWR | O_NOCTTY);
    if (master < 0) return -1;

    if (grantpt(master) != 0 || unlockpt(master) != 0) {
        close(master);
        return -1;
    }

    if (cols > 0 || rows > 0) {
        struct winsize ws;
        memset(&ws, 0, sizeof(ws));
        ws.ws_col = (unsigned short)(cols > 0 ? cols : 0);
        ws.ws_row = (unsigned short)(rows > 0 ? rows : 0);
        ioctl(master, TIOCSWINSZ, &ws);
    }

    return master;
}

JNIEXPORT jstring JNICALL
Java_com_winlator_cli_CliPty_getSlavePath(JNIEnv* env, jclass clazz, jint masterFd) {
    char* name = ptsname(masterFd);
    if (!name) return NULL;
    return (*env)->NewStringUTF(env, name);
}
