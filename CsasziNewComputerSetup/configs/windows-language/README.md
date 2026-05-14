# Windows language / locale settings

Snapshot of system + user language settings, applied by the
`windows-language` app in CsasziNewComputerSetup.

## Fields

| Field | Cmdlet | Admin? |
|-------|--------|--------|
| `user_language_list` | `Set-WinUserLanguageList` | no |
| `default_input_method` | `Set-WinDefaultInputMethodOverride` | no |
| `format_culture` | `Set-Culture` | no |
| `home_location_geo_id` | `Set-WinHomeLocation` | no |
| `system_locale` | `Set-WinSystemLocale` | **yes** + reboot |
| `copy_to_welcome_and_default_user` | `Copy-UserInternationalSettingsToSystem` | **yes** |

The custom Y/Z-swap input method `0409:A0000409` references the layout
installed by the `kbd-yz-swap` app, so it must run first.

## Refresh from current machine

```powershell
Get-WinUserLanguageList | ConvertTo-Json
Get-WinDefaultInputMethodOverride
Get-WinSystemLocale
Get-WinHomeLocation
Get-Culture
```

Update the JSON in this folder accordingly and commit.
