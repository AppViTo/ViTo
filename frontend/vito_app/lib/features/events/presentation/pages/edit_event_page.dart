import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:image_picker/image_picker.dart';
import 'package:intl/intl.dart';

import '../../domain/entities/event.dart';
import '../bloc/edit_event_cubit.dart';

/// Formatos y tamaño de portada que admite media-service (CONVENTIONS §8).
const _allowedCoverMimeTypes = ['image/jpeg', 'image/png', 'image/webp'];
const _maxCoverBytes = 10 * 1024 * 1024;

/// Edición del evento por su organizador (T049, Bug-F07): título, fecha,
/// portada, descripción, lugar, dirección, cupo, visibilidad y ocultar la
/// dirección. Solo se envía lo que cambió; los errores 400 y 409 muestran el
/// mensaje del backend. Al guardar, cierra la página devolviendo el evento.
class EditEventPage extends StatefulWidget {
  final Event event;

  const EditEventPage({super.key, required this.event});

  @override
  State<EditEventPage> createState() => _EditEventPageState();
}

class _EditEventPageState extends State<EditEventPage> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _title;
  late final TextEditingController _description;
  late final TextEditingController _location;
  late final TextEditingController _address;
  late final TextEditingController _capacity;

  DateTime? _startDate;
  DateTime? _endDate;
  late bool _isPublic;
  late bool _hideAddress;

  Event get _original => widget.event;

  @override
  void initState() {
    super.initState();
    final details = _original.details;
    final settings = _original.settings ?? const EventSettings();
    _title = TextEditingController(text: _original.title);
    _description = TextEditingController(text: _original.description ?? '');
    _location = TextEditingController(text: details?.location ?? '');
    _address = TextEditingController(text: details?.address ?? '');
    _capacity = TextEditingController(text: '${settings.maxAttendees}');
    _startDate = details?.startDate;
    _endDate = details?.endDate;
    _isPublic = settings.isPublic;
    _hideAddress = settings.hideAddressUntilAccepted;
  }

  @override
  void dispose() {
    _title.dispose();
    _description.dispose();
    _location.dispose();
    _address.dispose();
    _capacity.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return BlocConsumer<EditEventCubit, EditEventState>(
      listenWhen: (previous, current) =>
          current.error != null ||
          (current.status == EditEventStatus.saved &&
              previous.status != EditEventStatus.saved),
      listener: (context, state) {
        if (state.status == EditEventStatus.saved) {
          Navigator.of(context).pop(state.saved);
          return;
        }
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(state.error!)));
      },
      builder: (context, state) {
        final saving = state.status != EditEventStatus.editing;
        return Scaffold(
          appBar: AppBar(title: const Text('Editar evento')),
          body: Form(
            key: _formKey,
            child: ListView(
              padding: const EdgeInsets.all(16),
              children: [
                TextFormField(
                  key: const ValueKey('edit-title'),
                  controller: _title,
                  decoration: const InputDecoration(labelText: 'Nombre'),
                  maxLength: 100,
                  validator: (value) => (value ?? '').trim().length < 3
                      ? 'Mínimo 3 caracteres'
                      : null,
                ),
                TextFormField(
                  key: const ValueKey('edit-description'),
                  controller: _description,
                  decoration: const InputDecoration(labelText: 'Descripción'),
                  maxLength: 2000,
                  minLines: 2,
                  maxLines: 6,
                ),
                const SizedBox(height: 8),
                _dateTile(
                  label: 'Empieza',
                  value: _startDate,
                  onPicked: (date) => setState(() => _startDate = date),
                ),
                _dateTile(
                  label: 'Termina',
                  value: _endDate,
                  onPicked: (date) => setState(() => _endDate = date),
                ),
                const SizedBox(height: 8),
                _coverTile(context, state),
                const SizedBox(height: 8),
                TextFormField(
                  key: const ValueKey('edit-location'),
                  controller: _location,
                  decoration: const InputDecoration(
                    labelText: 'Lugar',
                    helperText: 'Barrio o nombre del sitio; lo ve cualquier invitado',
                  ),
                ),
                const SizedBox(height: 8),
                TextFormField(
                  key: const ValueKey('edit-address'),
                  controller: _address,
                  decoration: const InputDecoration(labelText: 'Dirección'),
                ),
                const SizedBox(height: 8),
                TextFormField(
                  key: const ValueKey('edit-capacity'),
                  controller: _capacity,
                  decoration: const InputDecoration(
                    labelText: 'Cupo',
                    helperText: '0 significa sin límite',
                  ),
                  keyboardType: TextInputType.number,
                  inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                  validator: (value) =>
                      int.tryParse(value ?? '') == null ? 'Escribe un número' : null,
                ),
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('Evento público'),
                  subtitle: const Text('Aparece en "Próximos eventos" para todos'),
                  value: _isPublic,
                  onChanged: (value) => setState(() => _isPublic = value),
                ),
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('Ocultar la dirección'),
                  subtitle: const Text('Solo la ven quienes confirmen que van'),
                  value: _hideAddress,
                  onChanged: (value) => setState(() => _hideAddress = value),
                ),
                const SizedBox(height: 16),
                ElevatedButton(
                  onPressed: saving || state.uploadingCover ? null : _save,
                  child: saving
                      ? const SizedBox(
                          height: 20,
                          width: 20,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Text('Guardar cambios'),
                ),
              ],
            ),
          ),
        );
      },
    );
  }

  Widget _dateTile({
    required String label,
    required DateTime? value,
    required ValueChanged<DateTime> onPicked,
  }) {
    final text = value == null
        ? 'Sin definir'
        : DateFormat("EEEE, d 'de' MMMM 'de' yyyy • h:mm a", 'es').format(value);
    return ListTile(
      contentPadding: EdgeInsets.zero,
      leading: const Icon(Icons.calendar_today),
      title: Text(label),
      subtitle: Text(text),
      onTap: () async {
        final initial = value ?? DateTime.now().add(const Duration(days: 1));
        final date = await showDatePicker(
          context: context,
          locale: const Locale('es'),
          initialDate: initial,
          firstDate: DateTime.now().subtract(const Duration(days: 1)),
          lastDate: DateTime.now().add(const Duration(days: 730)),
        );
        if (date == null || !mounted) return;
        final time = await showTimePicker(
          context: context,
          initialTime: TimeOfDay.fromDateTime(initial),
        );
        if (!mounted) return;
        onPicked(DateTime(
          date.year,
          date.month,
          date.day,
          time?.hour ?? initial.hour,
          time?.minute ?? initial.minute,
        ));
      },
    );
  }

  Widget _coverTile(BuildContext context, EditEventState state) {
    final covers = _original.details?.coverImages;
    final current = state.coverUrl ??
        (covers != null && covers.isNotEmpty ? covers.first : null);
    return ListTile(
      contentPadding: EdgeInsets.zero,
      leading: current != null
          ? ClipRRect(
              borderRadius: BorderRadius.circular(8),
              child: Image.network(
                current,
                width: 56,
                height: 56,
                fit: BoxFit.cover,
                errorBuilder: (_, __, ___) => const Icon(Icons.image),
              ),
            )
          : const Icon(Icons.image),
      title: const Text('Foto de portada'),
      subtitle: Text(state.uploadingCover
          ? 'Subiendo…'
          : state.coverUrl != null
              ? 'Nueva portada lista para guardar'
              : 'JPEG, PNG o WebP de hasta 10 MB'),
      trailing: TextButton(
        onPressed: state.uploadingCover ? null : _pickCover,
        child: const Text('Cambiar'),
      ),
    );
  }

  Future<void> _pickCover() async {
    final cubit = context.read<EditEventCubit>();
    final messenger = ScaffoldMessenger.of(context);
    try {
      final picked = await ImagePicker()
          .pickImage(source: ImageSource.gallery, imageQuality: 85);
      if (picked == null || !mounted) return;
      final bytes = await picked.readAsBytes();
      final mimeType = picked.mimeType ?? _mimeFromName(picked.name);
      if (!_allowedCoverMimeTypes.contains(mimeType)) {
        messenger.showSnackBar(const SnackBar(
            content: Text('La portada debe ser JPEG, PNG o WebP')));
        return;
      }
      if (bytes.lengthInBytes > _maxCoverBytes) {
        messenger.showSnackBar(
            const SnackBar(content: Text('La portada supera los 10 MB')));
        return;
      }
      await cubit.uploadCover(
        eventId: _original.id,
        bytes: bytes,
        fileName: picked.name,
        mimeType: mimeType,
      );
    } catch (_) {
      messenger.showSnackBar(
          const SnackBar(content: Text('No se pudo abrir la galería')));
    }
  }

  String _mimeFromName(String name) {
    final lower = name.toLowerCase();
    if (lower.endsWith('.png')) return 'image/png';
    if (lower.endsWith('.webp')) return 'image/webp';
    return 'image/jpeg';
  }

  void _save() {
    if (!(_formKey.currentState?.validate() ?? false)) return;
    final start = _startDate;
    final end = _endDate;
    if (start != null && end != null && !end.isAfter(start)) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
          content: Text('La fecha de fin debe ser posterior a la de inicio')));
      return;
    }

    final cubit = context.read<EditEventCubit>();
    final details = _original.details ?? const EventDetails();
    final settings = _original.settings ?? const EventSettings();
    final description = _description.text.trim();
    final location = _location.text.trim();
    final address = _address.text.trim();
    final coverUrl = cubit.state.coverUrl;

    final edited = Event(
      id: _original.id,
      organizerId: _original.organizerId,
      title: _title.text.trim(),
      description: description.isEmpty ? null : description,
      details: EventDetails(
        startDate: start,
        endDate: end,
        location: location.isEmpty ? null : location,
        address: address.isEmpty ? null : address,
        latitude: details.latitude,
        longitude: details.longitude,
        coverImages: coverUrl != null ? [coverUrl] : details.coverImages,
        tags: details.tags,
        category: details.category,
      ),
      settings: EventSettings(
        isPublic: _isPublic,
        requiresApproval: settings.requiresApproval,
        maxAttendees: int.parse(_capacity.text),
        allowGuestInvites: settings.allowGuestInvites,
        enableLiveStreaming: settings.enableLiveStreaming,
        enableMosaic: settings.enableMosaic,
        hideAddressUntilAccepted: _hideAddress,
      ),
      stats: _original.stats,
      status: _original.status,
      createdAt: _original.createdAt,
      updatedAt: _original.updatedAt,
    );
    cubit.save(original: _original, edited: edited);
  }
}
