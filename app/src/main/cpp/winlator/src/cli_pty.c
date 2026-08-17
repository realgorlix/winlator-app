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

    char* slaveName = ptsname(master);
    if (!slaveName) {
        close(master);
        return -1;
    }

    /*
     * Configure the line discipline before the child process attaches to the
     * slave. Put the pty in raw mode so that bytes written to the master reach
     * Wine's conhost --unix exactly as sent. This is important for input: a
     * carriage return (0x0D) is what conhost maps to VK_RETURN (Enter), while a
     * line feed (0x0A) becomes Ctrl+J. Leaving ICANON/ICRNL enabled would turn
     * a CR into NL and break console command input.
     */
    int slave = open(slaveName, O_RDWR | O_NOCTTY);
    if (slave >= 0) {
        struct termios tio;
        if (tcgetattr(slave, &tio) == 0) {
            tio.c_iflag &= ~(IGNBRK | BRKINT | PARMRK | ISTRIP | INLCR | IGNCR | ICRNL | IXON);
            tio.c_oflag &= ~OPOST;
            tio.c_lflag &= ~(ECHO | ECHONL | ICANON | ISIG | IEXTEN);
            tio.c_cflag &= ~(CSIZE | PARENB);
            tio.c_cflag |= CS8;
            tcsetattr(slave, TCSANOW, &tio);
        }
        close(slave);
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
