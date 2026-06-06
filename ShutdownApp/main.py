"""ShutdownApp - a small PyQt6 GUI to shut down the computer after a countdown."""

import subprocess
import sys

from PyQt6.QtCore import Qt, QTimer
from PyQt6.QtWidgets import (
    QApplication,
    QHBoxLayout,
    QLabel,
    QMessageBox,
    QPushButton,
    QSpinBox,
    QVBoxLayout,
    QWidget,
)


class ShutdownWindow(QWidget):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("Shutdown Timer")
        self.setMinimumWidth(320)

        self.remaining_seconds = 0
        self.timer = QTimer(self)
        self.timer.setInterval(1000)
        self.timer.timeout.connect(self._tick)

        self._build_ui()

    def _build_ui(self):
        layout = QVBoxLayout(self)

        # Hour / minute selector row
        selector_row = QHBoxLayout()

        self.hour_spin = QSpinBox()
        self.hour_spin.setRange(0, 23)
        self.hour_spin.setSuffix(" h")
        self.hour_spin.setValue(0)

        self.minute_spin = QSpinBox()
        self.minute_spin.setRange(0, 59)
        self.minute_spin.setSuffix(" min")
        self.minute_spin.setValue(30)

        selector_row.addWidget(QLabel("Shut down in:"))
        selector_row.addWidget(self.hour_spin)
        selector_row.addWidget(self.minute_spin)
        layout.addLayout(selector_row)

        # Countdown display
        self.countdown_label = QLabel("00:00:00")
        self.countdown_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        font = self.countdown_label.font()
        font.setPointSize(28)
        font.setBold(True)
        self.countdown_label.setFont(font)
        layout.addWidget(self.countdown_label)

        # Buttons row
        button_row = QHBoxLayout()
        self.start_button = QPushButton("Start")
        self.start_button.clicked.connect(self.start_countdown)
        self.stop_button = QPushButton("Stop")
        self.stop_button.clicked.connect(self.stop_countdown)
        self.stop_button.setEnabled(False)
        button_row.addWidget(self.start_button)
        button_row.addWidget(self.stop_button)
        layout.addLayout(button_row)

    def start_countdown(self):
        total = self.hour_spin.value() * 3600 + self.minute_spin.value() * 60
        if total <= 0:
            QMessageBox.warning(
                self, "Invalid time", "Please select a duration greater than zero."
            )
            return

        self.remaining_seconds = total
        self._update_label()
        self.timer.start()
        self._set_running(True)

    def stop_countdown(self):
        self.timer.stop()
        self.remaining_seconds = 0
        self._update_label()
        self._set_running(False)

    def _tick(self):
        self.remaining_seconds -= 1
        if self.remaining_seconds <= 0:
            self.timer.stop()
            self._update_label()
            self._set_running(False)
            self._shutdown()
            return
        self._update_label()

    def _shutdown(self):
        # /s = shutdown, /t 0 = no delay
        subprocess.Popen(["shutdown.exe", "/s", "/t", "0"])

    def _update_label(self):
        hours, remainder = divmod(max(self.remaining_seconds, 0), 3600)
        minutes, seconds = divmod(remainder, 60)
        self.countdown_label.setText(f"{hours:02d}:{minutes:02d}:{seconds:02d}")

    def _set_running(self, running):
        self.start_button.setEnabled(not running)
        self.stop_button.setEnabled(running)
        self.hour_spin.setEnabled(not running)
        self.minute_spin.setEnabled(not running)


def main():
    app = QApplication(sys.argv)
    window = ShutdownWindow()
    window.show()
    sys.exit(app.exec())


if __name__ == "__main__":
    main()
