# Architecture

* Codebase architecture should follow MVVM design pattern.
* When possible data should follow a uni-directional dataflow pattern with data streaming up from the data layers to the UI/UX layers and events streaming down from the UI/UX layers back to the data layers.
* When possible standard concurrency best-practices should be followed.
* Features are layered as Screen (Compose) → ViewModel → Repository → Data sources. See [Feature Layers](feature-layers.md) for the conventions of each layer, using the login screen as the reference.