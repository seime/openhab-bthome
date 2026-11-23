---
name: Bug report
about: Create a report to help us improve
title: "[bug] describe issue"
labels: bug
assignees: ''

---

**My setup**
- [ ] I'm running OH **FILL_IN_OH_VERSION**
- [ ] The BTHome device is **FILL_IN_MANUFACTURER_AND_MODEL** running firmware **FILL_IN_VERSION** (encrypted: **yes/no**)
- [ ] My Bluetooth bridge is **FILL_IN_BRIDGE** (e.g. BlueZ adapter, ESPHome Bluetooth proxy, ...)
- [ ] Output of `openhab-cli` command `bundle:list|grep BTHome` is **FILL_IN_OUTPUT**
- [ ] The version above is the newest release from https://github.com/seime/openhab-bthome/releases/latest (release name = bundle version)

**OH Logs**
- [ ] Binding logs from loggers `no.seime.openhab.binding.bluetooth.bthome` and `org.openhab.binding.bluetooth` at level `DEBUG` are attached (HOWTO: https://www.openhab.org/docs/administration/logging.html#defining-what-to-log)

**Describe the bug**
A clear and concise description of what the bug is.

**To Reproduce**
Steps to reproduce the behavior:
1. Go to '...'
2. Click on '....'
3. Scroll down to '....'
4. See error

**Expected behavior**
A clear and concise description of what you expected to happen.
