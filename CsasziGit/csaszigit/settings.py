"""
Settings persistence via QSettings.
"""

from PyQt6.QtCore import QSettings


class SettingsManager:
    """Thin wrapper around QSettings for CsasziGit."""

    def __init__(self):
        self._s = QSettings("Csaszi", "CsasziGit")

    # -- theme -----------------------------------------------------------------
    @property
    def theme(self) -> str:
        return str(self._s.value("theme", "dracula"))

    @theme.setter
    def theme(self, value: str):
        self._s.setValue("theme", value)

    # -- GPT -------------------------------------------------------------------
    @property
    def gpt_api_key(self) -> str:
        return str(self._s.value("gpt_api_key", ""))

    @gpt_api_key.setter
    def gpt_api_key(self, value: str):
        self._s.setValue("gpt_api_key", value)

    @property
    def gpt_model(self) -> str:
        return str(self._s.value("gpt_model", "gpt-4o-mini"))

    @gpt_model.setter
    def gpt_model(self, value: str):
        self._s.setValue("gpt_model", value)

    # -- last opened repo ------------------------------------------------------
    @property
    def last_repo(self) -> str:
        return str(self._s.value("last_repo", ""))

    @last_repo.setter
    def last_repo(self, value: str):
        self._s.setValue("last_repo", value)

    # -- window geometry -------------------------------------------------------
    @property
    def window_geometry(self):
        return self._s.value("window_geometry")

    @window_geometry.setter
    def window_geometry(self, value):
        self._s.setValue("window_geometry", value)

    @property
    def window_state(self):
        return self._s.value("window_state")

    @window_state.setter
    def window_state(self, value):
        self._s.setValue("window_state", value)
