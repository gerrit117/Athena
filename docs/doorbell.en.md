# Cameras, doorbell and door opener

## Select cameras

In the WebUI, open **Cameras & doorbell → Cameras in the overview**, select the cameras you want and save. The same selection is available on the Echo under **Cameras → Select cameras**. Selection uses names and entity IDs without loading images.

HA can expose vacuum maps and other images as `camera.*` entities. These do not have to appear in Athena's camera overview. An empty selection shows no cameras in the overview.

## Automatic doorbell view

Select the **Doorbell camera**, **Doorbell trigger**, duration and sound in **Cameras & doorbell**. The doorbell camera is independent of the overview selection. A new doorbell event opens it automatically, including from night view, and Athena subsequently returns to the previous screen.

## Optional door opener

In the WebUI, open **Cameras & doorbell → Door opener · optional**, select an appropriate HA entity and save. On the Echo it is also available in **Settings → Door opener · optional**. No selection means disabled.

The **Open door** button appears only in the automatic doorbell camera view. It sends the configured HA action and is disabled while HA or the entity is unavailable. Its response confirms the command, not the physical door position.

| Entity | HA action |
|---|---|
| `lock.*` | `lock.unlock`, or explicitly `lock.open` to unlatch |
| `button.*` / `input_button.*` | `press` |
| `script.*` | `turn_on` |
| `switch.*` / `input_boolean.*` | `turn_on` |
| `cover.*` | `open_cover` |

The lock must support unlatching. Configure any required relay pulse on the actuator or in HA. Athena turns a switch on and does not manage its timed turn-off. An HA script can combine multiple required steps.
