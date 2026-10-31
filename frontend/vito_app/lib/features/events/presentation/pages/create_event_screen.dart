import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:image_picker/image_picker.dart';
import 'package:intl/intl.dart';
import 'package:share_plus/share_plus.dart';

import '../../../../config/routes/app_router.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_spacing.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../../core/widgets/app_button.dart';
import '../../../../core/widgets/app_error.dart';
import '../../../social/domain/entities/social_profile.dart';
import '../../../social/presentation/bloc/social_bloc.dart';
import '../../domain/entities/event.dart';
import '../bloc/event_bloc.dart';

enum EventKind { birthday, meeting, wedding, graduation, travel, party }

class KindOption {
  final EventKind kind;
  final String label;
  final IconData icon;
  final String category;

  const KindOption(this.kind, this.label, this.icon, this.category);
}

const List<KindOption> eventKindOptions = [
  KindOption(EventKind.birthday, 'Cumpleaños', Icons.cake_outlined, 'BIRTHDAY'),
  KindOption(EventKind.meeting, 'Reunión', Icons.groups_2_outlined, 'MEETING'),
  KindOption(EventKind.wedding, 'Boda', Icons.favorite_outline, 'WEDDING'),
  KindOption(
      EventKind.graduation, 'Grado', Icons.school_outlined, 'GRADUATION'),
  KindOption(EventKind.travel, 'Viaje', Icons.flight_takeoff_outlined, 'TRAVEL'),
  KindOption(EventKind.party, 'Fiesta', Icons.celebration_outlined, 'PARTY'),
];

const List<String> _stepTitles = [
  'Nuevo evento',
  'Cuándo y dónde',
  '¿A quién invitamos?',
];

class CreateEventScreen extends StatefulWidget {
  const CreateEventScreen({super.key});

  @override
  State<CreateEventScreen> createState() => _CreateEventScreenState();
}

class _CreateEventScreenState extends State<CreateEventScreen> {
  static const int _stepCount = 3;
  static const int _maxCoverBytes = 10 * 1024 * 1024;
  static const List<String> _allowedCoverMimeTypes = [
    'image/jpeg',
    'image/png',
    'image/webp',
  ];

  int _currentStep = 0;
  bool _isSubmitting = false;
  bool _createHandled = false;
  bool _coverHandled = false;
  Event? _createdEvent;

  final _nameFormKey = GlobalKey<FormState>();
  final _whenFormKey = GlobalKey<FormState>();

  final _nameController = TextEditingController(text: 'Los 30 de Marta');
  final _messageController = TextEditingController();
  final _addressController = TextEditingController();
  final _searchController = TextEditingController();

  EventKind _kind = EventKind.birthday;
  late DateTime _startDate;
  late DateTime _endDate;

  bool _hideAddressUntilAccepted = true;
  bool _allowCompanion = false;

  Uint8List? _coverBytes;
  String? _coverFileName;
  String? _coverMimeType;
  String? _inviteLink;

  final List<SocialProfile> _invitees = [];
  List<SocialProfile> _searchResults = [];
  Timer? _searchDebounce;

  @override
  void initState() {
    super.initState();
    final base = DateTime.now().add(const Duration(days: 7));
    _startDate = DateTime(base.year, base.month, base.day, 20, 0);
    _endDate = _startDate.add(const Duration(hours: 6));
  }

  @override
  void dispose() {
    _searchDebounce?.cancel();
    _nameController.dispose();
    _messageController.dispose();
    _addressController.dispose();
    _searchController.dispose();
    super.dispose();
  }

  String get _categoryOfSelected =>
      eventKindOptions.firstWhere((o) => o.kind == _kind).category;

  String get _finalCtaLabel {
    final count = _invitees.length;
    if (count == 0) return 'Crear evento';
    return count == 1 ? 'Enviar 1 invitación' : 'Enviar $count invitaciones';
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.grisCarbon,
      resizeToAvoidBottomInset: true,
      body: SafeArea(
        child: BlocListener<EventBloc, EventState>(
          listener: _onEventState,
          child: Column(
            children: [
              WizardHeader(
                title: _stepTitles[_currentStep],
                currentStep: _currentStep,
                stepCount: _stepCount,
                onBack: _goBack,
              ),
              Expanded(
                child: AnimatedSwitcher(
                  duration: const Duration(milliseconds: 280),
                  switchInCurve: Curves.easeOutCubic,
                  switchOutCurve: Curves.easeInCubic,
                  transitionBuilder: (child, animation) {
                    final curved = CurvedAnimation(
                      parent: animation,
                      curve: Curves.easeOutCubic,
                    );
                    return FadeTransition(
                      opacity: curved,
                      child: SlideTransition(
                        position: Tween<Offset>(
                          begin: const Offset(0.05, 0),
                          end: Offset.zero,
                        ).animate(curved),
                        child: child,
                      ),
                    );
                  },
                  child: _buildCurrentStep(),
                ),
              ),
              _buildBottomBar(),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildCurrentStep() {
    switch (_currentStep) {
      case 0:
        return _buildStepIdentity(key: const ValueKey(0));
      case 1:
        return _buildStepWhenWhere(key: const ValueKey(1));
      default:
        return _buildStepGuests(key: const ValueKey(2));
    }
  }

  // ── Paso 1: Nuevo evento ────────────────────────────────────────────────

  Widget _buildStepIdentity({Key? key}) {
    return SingleChildScrollView(
      key: key,
      padding: const EdgeInsets.fromLTRB(
        AppSpacing.xl,
        AppSpacing.lg,
        AppSpacing.xl,
        AppSpacing.xl,
      ),
      child: Form(
        key: _nameFormKey,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            CoverPicker(
              imageBytes: _coverBytes,
              onTap: _pickCover,
            ),
            const SizedBox(height: AppSpacing.xxl),
            WizardField(
              label: 'NOMBRE DEL EVENTO',
              controller: _nameController,
              hint: 'Ej. Los 30 de Marta',
              prefixIcon: Icons.event_outlined,
              textInputAction: TextInputAction.next,
              validator: (value) {
                final text = (value ?? '').trim();
                if (text.isEmpty) return 'Escribe el nombre del evento';
                if (text.length < 3) return 'Mínimo 3 caracteres';
                if (text.length > 100) return 'Máximo 100 caracteres';
                return null;
              },
            ),
            const SizedBox(height: AppSpacing.xl),
            Text(
              'TIPO DE EVENTO',
              style: AppTypography.labelSmall.copyWith(
                color: AppColors.textMuted,
                letterSpacing: 1.2,
              ),
            ),
            const SizedBox(height: AppSpacing.md),
            Wrap(
              spacing: AppSpacing.sm,
              runSpacing: AppSpacing.sm,
              children: [
                for (final option in eventKindOptions)
                  OptionChip(
                    label: option.label,
                    icon: option.icon,
                    selected: _kind == option.kind,
                    onTap: () => setState(() => _kind = option.kind),
                  ),
              ],
            ),
            const SizedBox(height: AppSpacing.xl),
            WizardField(
              label: 'MENSAJE PARA TUS INVITADOS',
              controller: _messageController,
              hint: 'Cuéntales de qué se trata esta celebración',
              maxLines: 4,
              maxLength: 2000,
              prefixIcon: Icons.notes_outlined,
              validator: (value) {
                if ((value ?? '').length > 2000) {
                  return 'Máximo 2000 caracteres';
                }
                return null;
              },
            ),
          ],
        ),
      ),
    );
  }

  // ── Paso 2: Cuándo y dónde ──────────────────────────────────────────────

  Widget _buildStepWhenWhere({Key? key}) {
    return SingleChildScrollView(
      key: key,
      padding: const EdgeInsets.fromLTRB(
        AppSpacing.xl,
        AppSpacing.lg,
        AppSpacing.xl,
        AppSpacing.xl,
      ),
      child: Form(
        key: _whenFormKey,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              'FECHA Y HORARIO',
              style: AppTypography.labelSmall.copyWith(
                color: AppColors.textMuted,
                letterSpacing: 1.2,
              ),
            ),
            const SizedBox(height: AppSpacing.md),
            SurfacePanel(
              onTap: _pickDates,
              child: Row(
                children: [
                  const Icon(
                    Icons.calendar_month_outlined,
                    color: AppColors.coralNeon,
                    size: AppSpacing.iconLg,
                  ),
                  const SizedBox(width: AppSpacing.md),
                  Expanded(
                    child: Text(
                      _formatDateRange(),
                      style: AppTypography.bodyMedium.copyWith(
                        color: AppColors.white,
                        height: 1.4,
                      ),
                    ),
                  ),
                  const SizedBox(width: AppSpacing.sm),
                  Text(
                    'Cambiar',
                    style: AppTypography.labelMedium.copyWith(
                      color: AppColors.coralNeon,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: AppSpacing.xl),
            WizardField(
              label: 'DIRECCIÓN',
              controller: _addressController,
              hint: 'Cra. 7 #63-14, Chapinero Alto',
              prefixIcon: Icons.place_outlined,
              validator: (value) {
                if ((value ?? '').length > 300) return 'Máximo 300 caracteres';
                return null;
              },
            ),
            const SizedBox(height: AppSpacing.md),
            const MiniMapView(),
            const SizedBox(height: AppSpacing.xl),
            SwitchPanel(
              title: 'Mostrar solo el barrio hasta que acepten',
              description:
                  'Quien no ha aceptado ve “Chapinero Alto”. La dirección exacta aparece al confirmar.',
              value: _hideAddressUntilAccepted,
              onChanged: (value) =>
                  setState(() => _hideAddressUntilAccepted = value),
            ),
          ],
        ),
      ),
    );
  }

  // ── Paso 3: ¿A quién invitamos? ─────────────────────────────────────────

  Widget _buildStepGuests({Key? key}) {
    return BlocConsumer<SocialBloc, SocialState>(
      key: key,
      listener: _onSocialState,
      builder: (context, socialState) {
        final isSearching = socialState is SocialLoading;
        return SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(
            AppSpacing.xl,
            AppSpacing.lg,
            AppSpacing.xl,
            AppSpacing.xl,
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              TextField(
                controller: _searchController,
                onChanged: _onSearchChanged,
                style: AppTypography.bodyMedium.copyWith(color: AppColors.white),
                decoration: InputDecoration(
                  hintText: 'Buscar por nombre o @usuario',
                  hintStyle: AppTypography.bodyMedium
                      .copyWith(color: AppColors.textMuted),
                  prefixIcon: const Icon(
                    Icons.search,
                    color: AppColors.textMuted,
                    size: AppSpacing.iconMd,
                  ),
                  suffixIcon: isSearching
                      ? const SizedBox(
                          width: 18,
                          height: 18,
                          child: Padding(
                            padding: EdgeInsets.all(12),
                            child: CircularProgressIndicator(
                              strokeWidth: 2,
                              color: AppColors.coralNeon,
                            ),
                          ),
                        )
                      : null,
                  filled: true,
                  fillColor: AppColors.surfaceElevated,
                  contentPadding: const EdgeInsets.symmetric(
                    horizontal: AppSpacing.lg,
                    vertical: AppSpacing.md,
                  ),
                  border: OutlineInputBorder(
                    borderRadius:
                        BorderRadius.circular(AppSpacing.radiusLg),
                    borderSide: const BorderSide(color: AppColors.stepTrack),
                  ),
                  enabledBorder: OutlineInputBorder(
                    borderRadius:
                        BorderRadius.circular(AppSpacing.radiusLg),
                    borderSide: const BorderSide(color: AppColors.stepTrack),
                  ),
                  focusedBorder: OutlineInputBorder(
                    borderRadius:
                        BorderRadius.circular(AppSpacing.radiusLg),
                    borderSide: const BorderSide(
                        color: AppColors.coralNeon, width: 1.5),
                  ),
                ),
              ),
              if (_invitees.isNotEmpty) ...[
                const SizedBox(height: AppSpacing.lg),
                _buildSelectedChips(),
              ],
              if (_searchResults.isNotEmpty) ...[
                const SizedBox(height: AppSpacing.lg),
                _buildSearchResults(),
              ] else if (socialState is SearchResultsLoaded &&
                  socialState.users.isEmpty &&
                  _searchController.text.trim().length >= 2) ...[
                const SizedBox(height: AppSpacing.lg),
                const SurfacePanel(
                  child: Text(
                    'Sin coincidencias. Prueba con otro nombre o @usuario.',
                    style: AppTypography.bodySmall,
                    textAlign: TextAlign.center,
                  ),
                ),
              ],
              const SizedBox(height: AppSpacing.xxl),
              _buildLinkSection(),
              const SizedBox(height: AppSpacing.lg),
              SwitchPanel(
                title: 'Permitir que traigan acompañante',
                description:
                    'Cada invitado aceptado podrá sumar una persona extra a su confirmación.',
                value: _allowCompanion,
                enabled: false,
                onChanged: (value) => setState(() => _allowCompanion = value),
              ),
            ],
          ),
        );
      },
    );
  }

  Widget _buildSelectedChips() {
    final visible = _invitees.take(3).toList();
    final overflow = _invitees.length - visible.length;
    return SizedBox(
      height: 40,
      child: ListView(
        scrollDirection: Axis.horizontal,
        children: [
          for (final profile in visible)
            Padding(
              padding: const EdgeInsets.only(right: AppSpacing.sm),
              child: InviteeChip(
                label: _displayName(profile),
                showCheck: true,
                onRemove: () => _removeInvitee(profile),
              ),
            ),
          if (overflow > 0)
            InviteeChip(
              label: '+$overflow más',
              showCheck: false,
            ),
        ],
      ),
    );
  }

  Widget _buildSearchResults() {
    return SurfacePanel(
      padding: const EdgeInsets.symmetric(vertical: AppSpacing.xs),
      child: Material(
        type: MaterialType.transparency,
        child: Column(
          children: [
            for (var i = 0; i < _searchResults.length; i++) ...[
              if (i > 0)
                const Divider(
                  height: 1,
                  indent: AppSpacing.lg,
                  endIndent: AppSpacing.lg,
                  color: AppColors.stepTrack,
                ),
              ListTile(
                dense: true,
                onTap: () => _addInvitee(_searchResults[i]),
                leading: _Avatar(profile: _searchResults[i]),
                title: Text(
                  _displayName(_searchResults[i]),
                  style: AppTypography.bodyMedium
                      .copyWith(color: AppColors.white),
                ),
                subtitle: Text(
                  '@${_searchResults[i].username}',
                  style: AppTypography.bodySmall
                      .copyWith(color: AppColors.textMuted),
                ),
                trailing: IconButton(
                  icon: const Icon(
                    Icons.add_circle_outline,
                    color: AppColors.coralNeon,
                  ),
                  onPressed: () => _addInvitee(_searchResults[i]),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildLinkSection() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'COMPARTE EL ENLACE',
          style: AppTypography.overline.copyWith(
            color: AppColors.textMuted,
            letterSpacing: 1.5,
          ),
        ),
        const SizedBox(height: AppSpacing.md),
        LinkActionCard(
          icon: Icons.link,
          title: 'Copiar enlace de invitación',
          subtitle: _inviteLink != null
              ? 'Caduca en 7 días'
              : 'Se crea con el evento y caduca en 7 días',
          actionLabel: 'Copiar',
          onTap: _copyInviteLink,
        ),
        const SizedBox(height: AppSpacing.md),
        LinkActionCard(
          icon: Icons.chat_bubble_outline,
          title: 'Enviar por WhatsApp',
          subtitle: 'Comparte el enlace con quien quieras',
          actionLabel: 'Abrir',
          onTap: _shareInviteLink,
        ),
      ],
    );
  }

  // ── Chrome ──────────────────────────────────────────────────────────────

  Widget _buildBottomBar() {
    final label =
        _currentStep < _stepCount - 1 ? 'Continuar' : _finalCtaLabel;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.fromLTRB(
        AppSpacing.xl,
        AppSpacing.md,
        AppSpacing.xl,
        AppSpacing.lg,
      ),
      decoration: const BoxDecoration(
        color: AppColors.grisCarbon,
        border: Border(top: BorderSide(color: AppColors.stepTrack)),
      ),
      child: AppButton(
        text: label,
        onPressed: _isSubmitting ? null : _onPrimaryAction,
        type: AppButtonType.primary,
        size: AppButtonSize.large,
        isFullWidth: true,
        isLoading: _isSubmitting,
      ),
    );
  }

  // ── Lógica ──────────────────────────────────────────────────────────────

  void _goBack() {
    if (_currentStep > 0) {
      setState(() => _currentStep--);
      return;
    }
    Navigator.of(context).maybePop();
  }

  void _onPrimaryAction() {
    if (_currentStep < _stepCount - 1) {
      if (!_validateCurrentStep()) return;
      FocusScope.of(context).unfocus();
      setState(() => _currentStep++);
      return;
    }
    _submit();
  }

  bool _validateCurrentStep() {
    switch (_currentStep) {
      case 0:
        return _nameFormKey.currentState?.validate() ?? false;
      case 1:
        final formOk = _whenFormKey.currentState?.validate() ?? false;
        if (!formOk) return false;
        if (!_endDate.isAfter(_startDate)) {
          _showInfo('La fecha de fin debe ser posterior a la de inicio');
          return false;
        }
        return true;
      default:
        return true;
    }
  }

  void _submit() {
    setState(() => _isSubmitting = true);
    final message = _messageController.text.trim();
    final address = _addressController.text.trim();

    context.read<EventBloc>().add(CreateEvent(
          title: _nameController.text.trim(),
          description: message.isEmpty ? null : message,
          details: EventDetails(
            startDate: _startDate,
            endDate: _endDate,
            address: address.isEmpty ? null : address,
            category: _categoryOfSelected,
          ),
          settings: EventSettings(
            isPublic: false,
            allowGuestInvites: _allowCompanion,
            enableLiveStreaming: true,
            enableMosaic: true,
            hideAddressUntilAccepted: _hideAddressUntilAccepted,
          ),
        ));
  }

  void _onEventState(BuildContext context, EventState state) {
    if (state is EventCreated && _isSubmitting && !_createHandled) {
      _createHandled = true;
      _createdEvent = state.event;

      final bytes = _coverBytes;
      if (bytes != null) {
        context.read<EventBloc>().add(UploadEventCover(
              eventId: state.event.id,
              bytes: bytes,
              fileName: _coverFileName ?? 'portada.jpg',
              mimeType: _coverMimeType ?? 'image/jpeg',
            ));
        return;
      }

      _finishCreate();
      return;
    }

    if (state is EventCoverUploaded && _isSubmitting && !_coverHandled) {
      _coverHandled = true;
      _finishCreate();
      return;
    }

    if (state is EventCoverUploadFailed && _isSubmitting && !_coverHandled) {
      _coverHandled = true;
      _finishCreate(coverFailed: true);
      return;
    }

    if (state is EventError && _isSubmitting) {
      setState(() => _isSubmitting = false);
      AppErrorSnackBar.show(context, message: state.message);
    }
  }

  void _finishCreate({bool coverFailed = false}) {
    final event = _createdEvent;
    if (event == null) return;

    setState(() => _isSubmitting = false);

    if (coverFailed) {
      _showInfo('Evento creado. No se pudo subir la portada.');
    } else {
      AppSuccessSnackBar.show(
        context,
        message: 'Evento “${event.title}” creado',
      );
    }

    Navigator.pushReplacementNamed(
      context,
      AppRouter.eventDetail,
      arguments: {'eventId': event.id},
    );
  }

  // ── Fechas ──────────────────────────────────────────────────────────────

  Future<void> _pickDates() async {
    final range = await showDateRangePicker(
      context: context,
      locale: const Locale('es'),
      firstDate: DateTime.now(),
      lastDate: DateTime.now().add(const Duration(days: 365)),
      initialDateRange: DateTimeRange(start: _startDate, end: _endDate),
    );
    if (range == null || !mounted) return;

    final startTime = await showTimePicker(
      context: context,
      initialTime: TimeOfDay.fromDateTime(_startDate),
    );
    if (!mounted) return;

    final endTime = await showTimePicker(
      context: context,
      initialTime: TimeOfDay.fromDateTime(_endDate),
    );
    if (!mounted) return;

    setState(() {
      _startDate = DateTime(
        range.start.year,
        range.start.month,
        range.start.day,
        startTime?.hour ?? _startDate.hour,
        startTime?.minute ?? _startDate.minute,
      );
      _endDate = DateTime(
        range.end.year,
        range.end.month,
        range.end.day,
        endTime?.hour ?? _endDate.hour,
        endTime?.minute ?? _endDate.minute,
      );
      if (!_endDate.isAfter(_startDate)) {
        _endDate = _startDate.add(const Duration(hours: 6));
      }
    });
  }

  String _formatDateRange() {
    const locale = 'es';
    final startDay =
        DateFormat("EEEE, d 'de' MMMM", locale).format(_startDate);
    final startTime =
        _normalizeTime(DateFormat('h:mm a', locale).format(_startDate));
    final endTime =
        _normalizeTime(DateFormat('h:mm a', locale).format(_endDate));
    final startLabel = _capitalize('$startDay, $startTime');

    final sameDay = _startDate.year == _endDate.year &&
        _startDate.month == _endDate.month &&
        _startDate.day == _endDate.day;
    if (sameDay) return '$startLabel — $endTime';

    final endDay = DateFormat("EEEE, d 'de' MMMM", locale).format(_endDate);
    return '$startLabel — ${_capitalize('$endDay, $endTime')}';
  }

  String _normalizeTime(String value) =>
      value.replaceAll('\u202F', ' ').replaceAll('\u00A0', ' ');

  String _capitalize(String value) {
    if (value.isEmpty) return value;
    return value[0].toUpperCase() + value.substring(1);
  }

  // ── Portada ─────────────────────────────────────────────────────────────

  Future<void> _pickCover() async {
    try {
      final picked = await ImagePicker().pickImage(
        source: ImageSource.gallery,
        imageQuality: 85,
      );
      if (picked == null || !mounted) return;

      final bytes = await picked.readAsBytes();
      if (!mounted) return;

      final mimeType = picked.mimeType ?? _mimeFromName(picked.name);
      if (!_allowedCoverMimeTypes.contains(mimeType)) {
        _showInfo('La portada debe ser JPEG, PNG o WebP');
        return;
      }
      if (bytes.lengthInBytes > _maxCoverBytes) {
        _showInfo('La portada supera los 10 MB');
        return;
      }

      setState(() {
        _coverBytes = bytes;
        _coverFileName = picked.name;
        _coverMimeType = mimeType;
      });
    } catch (_) {
      if (mounted) _showInfo('No se pudo abrir la galería');
    }
  }

  String _mimeFromName(String name) {
    final lower = name.toLowerCase();
    if (lower.endsWith('.png')) return 'image/png';
    if (lower.endsWith('.webp')) return 'image/webp';
    return 'image/jpeg';
  }

  // ── Invitados ───────────────────────────────────────────────────────────

  void _onSearchChanged(String query) {
    _searchDebounce?.cancel();
    final trimmed = query.trim();
    if (trimmed.isEmpty) {
      setState(() => _searchResults = []);
      return;
    }
    _searchDebounce = Timer(const Duration(milliseconds: 350), () {
      if (!mounted) return;
      context.read<SocialBloc>().add(SearchUsers(trimmed));
    });
  }

  void _onSocialState(BuildContext context, SocialState state) {
    if (state is SearchResultsLoaded) {
      setState(() {
        _searchResults = state.users
            .where((user) => !_invitees.any((i) => i.userId == user.userId))
            .toList();
      });
    } else if (state is SocialError) {
      _searchResults = [];
    }
  }

  void _addInvitee(SocialProfile profile) {
    setState(() {
      if (!_invitees.any((i) => i.userId == profile.userId)) {
        _invitees.add(profile);
      }
      _searchResults =
          _searchResults.where((u) => u.userId != profile.userId).toList();
    });
  }

  void _removeInvitee(SocialProfile profile) {
    setState(() => _invitees.removeWhere((i) => i.userId == profile.userId));
  }

  String _displayName(SocialProfile profile) => profile.displayName.isNotEmpty
      ? profile.displayName
      : profile.username;

  // ── Enlace ──────────────────────────────────────────────────────────────

  void _copyInviteLink() {
    if (_inviteLink == null) {
      _showInfo('El enlace se genera al crear el evento');
      return;
    }
    Clipboard.setData(ClipboardData(text: _inviteLink!));
    _showSuccess('Enlace copiado');
  }

  void _shareInviteLink() {
    if (_inviteLink == null) {
      _showInfo('El enlace se genera al crear el evento');
      return;
    }
    SharePlus.instance.share(ShareParams(text: 'Te invito a mi evento en ViTo: $_inviteLink'));
  }

  // ── Feedback ────────────────────────────────────────────────────────────

  void _showSuccess(String message) {
    if (!mounted) return;
    AppSuccessSnackBar.show(context, message: message);
  }

  void _showInfo(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: AppColors.surfaceElevated,
        behavior: SnackBarBehavior.floating,
      ),
    );
  }
}

// ── Widgets reutilizables ────────────────────────────────────────────────

class WizardHeader extends StatelessWidget {
  final String title;
  final int currentStep;
  final int stepCount;
  final VoidCallback onBack;

  const WizardHeader({
    super.key,
    required this.title,
    required this.currentStep,
    required this.stepCount,
    required this.onBack,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(
        AppSpacing.md,
        AppSpacing.sm,
        AppSpacing.xl,
        AppSpacing.lg,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              IconButton(
                onPressed: onBack,
                icon: const Icon(
                  Icons.arrow_back_ios_new,
                  color: AppColors.white,
                  size: AppSpacing.iconMd,
                ),
              ),
              const SizedBox(width: AppSpacing.xs),
              Expanded(
                child: Text(
                  title,
                  style: AppTypography.h5.copyWith(color: AppColors.white),
                ),
              ),
            ],
          ),
          const SizedBox(height: AppSpacing.md),
          Row(
            children: [
              for (var i = 0; i < stepCount; i++)
                Expanded(
                  child: AnimatedContainer(
                    duration: const Duration(milliseconds: 300),
                    curve: Curves.easeOutCubic,
                    height: 4,
                    margin: i < stepCount - 1
                        ? const EdgeInsets.only(right: AppSpacing.xs)
                        : EdgeInsets.zero,
                    decoration: BoxDecoration(
                      color:
                          i == currentStep ? AppColors.coralNeon : AppColors.stepTrack,
                      borderRadius: BorderRadius.circular(AppSpacing.radiusRound),
                    ),
                  ),
                ),
            ],
          ),
        ],
      ),
    );
  }
}

class SurfacePanel extends StatelessWidget {
  final Widget child;
  final VoidCallback? onTap;
  final EdgeInsetsGeometry padding;

  const SurfacePanel({
    super.key,
    required this.child,
    this.onTap,
    this.padding = const EdgeInsets.all(AppSpacing.lg),
  });

  @override
  Widget build(BuildContext context) {
    final content = Container(
      width: double.infinity,
      padding: padding,
      decoration: BoxDecoration(
        color: AppColors.surfaceElevated,
        borderRadius: BorderRadius.circular(AppSpacing.radiusLg),
        border: Border.all(color: AppColors.stepTrack),
      ),
      child: child,
    );
    if (onTap == null) return content;
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(AppSpacing.radiusLg),
        child: content,
      ),
    );
  }
}

class WizardField extends StatelessWidget {
  final String label;
  final TextEditingController controller;
  final String? hint;
  final IconData? prefixIcon;
  final int maxLines;
  final int? maxLength;
  final TextInputAction textInputAction;
  final String? Function(String?)? validator;

  const WizardField({
    super.key,
    required this.label,
    required this.controller,
    this.hint,
    this.prefixIcon,
    this.maxLines = 1,
    this.maxLength,
    this.textInputAction = TextInputAction.done,
    this.validator,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label,
          style: AppTypography.labelSmall.copyWith(
            color: AppColors.textMuted,
            letterSpacing: 1.2,
          ),
        ),
        const SizedBox(height: AppSpacing.sm),
        TextFormField(
          controller: controller,
          maxLines: maxLines,
          maxLength: maxLength,
          textInputAction: textInputAction,
          validator: validator,
          style: AppTypography.bodyMedium.copyWith(color: AppColors.white),
          cursorColor: AppColors.coralNeon,
          decoration: InputDecoration(
            hintText: hint,
            hintStyle: AppTypography.bodyMedium
                .copyWith(color: AppColors.textMuted),
            prefixIcon: prefixIcon != null
                ? Icon(prefixIcon, color: AppColors.textMuted, size: AppSpacing.iconMd)
                : null,
            filled: true,
            fillColor: AppColors.surfaceElevated,
            counterStyle: AppTypography.labelSmall
                .copyWith(color: AppColors.textMuted),
            contentPadding: EdgeInsets.symmetric(
              horizontal: AppSpacing.lg,
              vertical: maxLines > 1 ? AppSpacing.md : AppSpacing.lg,
            ),
            border: OutlineInputBorder(
              borderRadius: BorderRadius.circular(AppSpacing.radiusLg),
              borderSide: const BorderSide(color: AppColors.stepTrack),
            ),
            enabledBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(AppSpacing.radiusLg),
              borderSide: const BorderSide(color: AppColors.stepTrack),
            ),
            focusedBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(AppSpacing.radiusLg),
              borderSide:
                  const BorderSide(color: AppColors.coralNeon, width: 1.5),
            ),
            errorBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(AppSpacing.radiusLg),
              borderSide: const BorderSide(color: AppColors.error),
            ),
            focusedErrorBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(AppSpacing.radiusLg),
              borderSide: const BorderSide(color: AppColors.error, width: 1.5),
            ),
          ),
        ),
      ],
    );
  }
}

class OptionChip extends StatelessWidget {
  final String label;
  final IconData icon;
  final bool selected;
  final VoidCallback onTap;

  const OptionChip({
    super.key,
    required this.label,
    required this.icon,
    required this.selected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(AppSpacing.radiusRound),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 200),
          padding: const EdgeInsets.symmetric(
            horizontal: AppSpacing.lg,
            vertical: AppSpacing.sm,
          ),
          decoration: BoxDecoration(
            color: selected
                ? AppColors.coralNeon.withValues(alpha: 0.14)
                : AppColors.surfaceElevated,
            borderRadius: BorderRadius.circular(AppSpacing.radiusRound),
            border: Border.all(
              color: selected ? AppColors.coralNeon : AppColors.stepTrack,
              width: selected ? 1.5 : 1,
            ),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                icon,
                size: AppSpacing.iconSm,
                color: selected ? AppColors.coralNeon : AppColors.textMuted,
              ),
              const SizedBox(width: AppSpacing.xs),
              Text(
                label,
                style: AppTypography.labelMedium.copyWith(
                  color: selected ? AppColors.white : AppColors.textMuted,
                  fontWeight: selected ? FontWeight.w700 : FontWeight.w500,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class SwitchPanel extends StatelessWidget {
  final String title;
  final String description;
  final bool value;
  final bool enabled;
  final ValueChanged<bool> onChanged;

  const SwitchPanel({
    super.key,
    required this.title,
    required this.description,
    required this.value,
    required this.onChanged,
    this.enabled = true,
  });

  @override
  Widget build(BuildContext context) {
    return SurfacePanel(
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: AppTypography.bodyMedium.copyWith(
                    color: AppColors.white,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(height: AppSpacing.xs),
                Text(
                  description,
                  style: AppTypography.bodySmall
                      .copyWith(color: AppColors.textMuted, height: 1.5),
                ),
              ],
            ),
          ),
          const SizedBox(width: AppSpacing.md),
          Switch(
            value: value,
            onChanged: enabled ? onChanged : null,
            activeThumbColor: AppColors.coralNeon,
            activeTrackColor: AppColors.coralNeon.withValues(alpha: 0.35),
            inactiveTrackColor: AppColors.stepTrack,
            trackOutlineColor: const WidgetStatePropertyAll(Colors.transparent),
          ),
        ],
      ),
    );
  }
}

class LinkActionCard extends StatelessWidget {
  final IconData icon;
  final String title;
  final String subtitle;
  final String actionLabel;
  final VoidCallback onTap;

  const LinkActionCard({
    super.key,
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.actionLabel,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return SurfacePanel(
      padding: const EdgeInsets.symmetric(
        horizontal: AppSpacing.lg,
        vertical: AppSpacing.md,
      ),
      child: Row(
        children: [
          Container(
            width: 40,
            height: 40,
            decoration: BoxDecoration(
              color: AppColors.coralNeon.withValues(alpha: 0.14),
              borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
            ),
            child: Icon(icon, color: AppColors.coralNeon, size: AppSpacing.iconMd),
          ),
          const SizedBox(width: AppSpacing.md),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: AppTypography.bodyMedium.copyWith(
                    color: AppColors.white,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(height: AppSpacing.xxxs),
                Text(
                  subtitle,
                  style: AppTypography.bodySmall
                      .copyWith(color: AppColors.textMuted),
                ),
              ],
            ),
          ),
          const SizedBox(width: AppSpacing.sm),
          TextButton(
            onPressed: onTap,
            style: TextButton.styleFrom(
              foregroundColor: AppColors.coralNeon,
              padding: const EdgeInsets.symmetric(horizontal: AppSpacing.md),
              minimumSize: Size.zero,
              textStyle: AppTypography.labelMedium
                  .copyWith(fontWeight: FontWeight.w700),
            ),
            child: Text(actionLabel),
          ),
        ],
      ),
    );
  }
}

class InviteeChip extends StatelessWidget {
  final String label;
  final bool showCheck;
  final VoidCallback? onRemove;

  const InviteeChip({
    super.key,
    required this.label,
    this.showCheck = false,
    this.onRemove,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.only(right: AppSpacing.xs),
      decoration: BoxDecoration(
        color: AppColors.surfaceElevated,
        borderRadius: BorderRadius.circular(AppSpacing.radiusRound),
        border: Border.all(color: AppColors.coralNeon),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          const SizedBox(width: AppSpacing.md),
          if (showCheck)
            const Icon(
              Icons.check_circle,
              size: AppSpacing.iconSm,
              color: AppColors.coralNeon,
            ),
          if (showCheck) const SizedBox(width: AppSpacing.xs),
          Text(
            label,
            style: AppTypography.labelMedium.copyWith(
              color: AppColors.white,
              fontWeight: FontWeight.w600,
            ),
          ),
          if (onRemove != null)
            IconButton(
              onPressed: onRemove,
              icon: const Icon(
                Icons.close,
                size: AppSpacing.iconSm,
                color: AppColors.textMuted,
              ),
              visualDensity: VisualDensity.compact,
              padding: const EdgeInsets.only(left: AppSpacing.xs),
              constraints: const BoxConstraints(minWidth: 28, minHeight: 28),
            )
          else
            const SizedBox(width: AppSpacing.md),
        ],
      ),
    );
  }
}

class CoverPicker extends StatelessWidget {
  final Uint8List? imageBytes;
  final VoidCallback onTap;

  const CoverPicker({super.key, required this.imageBytes, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(AppSpacing.radiusXl),
        child: Container(
          height: 170,
          clipBehavior: Clip.antiAlias,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(AppSpacing.radiusXl),
            border: Border.all(color: AppColors.stepTrack),
            gradient: const LinearGradient(
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
              colors: [Color(0xFF1B2236), Color(0xFF241B2E)],
            ),
          ),
          child: Stack(
            fit: StackFit.expand,
            children: [
              if (imageBytes != null)
                Image.memory(imageBytes!, fit: BoxFit.cover),
              Center(
                child: Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: AppSpacing.lg,
                    vertical: AppSpacing.md,
                  ),
                  decoration: BoxDecoration(
                    color: Colors.black.withValues(alpha: 0.45),
                    borderRadius:
                        BorderRadius.circular(AppSpacing.radiusRound),
                    border: Border.all(color: Colors.white24),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(
                        Icons.photo_camera_outlined,
                        color: AppColors.white,
                        size: AppSpacing.iconMd,
                      ),
                      const SizedBox(width: AppSpacing.sm),
                      Text(
                        '+ Cambiar foto de portada',
                        style: AppTypography.labelMedium.copyWith(
                          color: AppColors.white,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class MiniMapView extends StatelessWidget {
  const MiniMapView({super.key});

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 160,
      clipBehavior: Clip.antiAlias,
      decoration: BoxDecoration(
        color: AppColors.surfaceElevated,
        borderRadius: BorderRadius.circular(AppSpacing.radiusLg),
        border: Border.all(color: AppColors.stepTrack),
      ),
      child: CustomPaint(
        painter: _MiniMapPainter(),
        child: Center(
          child: Container(
            width: 56,
            height: 56,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: AppColors.coralNeon.withValues(alpha: 0.18),
              boxShadow: [
                BoxShadow(
                  color: AppColors.coralNeon.withValues(alpha: 0.35),
                  blurRadius: 24,
                  spreadRadius: 4,
                ),
              ],
            ),
            child: Center(
              child: Container(
                width: 26,
                height: 26,
                decoration: const BoxDecoration(
                  shape: BoxShape.circle,
                  color: AppColors.coralNeon,
                ),
                child: const Icon(
                  Icons.location_on,
                  size: AppSpacing.iconSm,
                  color: AppColors.white,
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _MiniMapPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final grid = Paint()
      ..color = AppColors.stepTrack
      ..strokeWidth = 1;

    const step = 26.0;
    for (double x = 0; x <= size.width; x += step) {
      canvas.drawLine(Offset(x, 0), Offset(x, size.height), grid);
    }
    for (double y = 0; y <= size.height; y += step) {
      canvas.drawLine(Offset(0, y), Offset(size.width, y), grid);
    }

    final route = Paint()
      ..color = AppColors.lavandaPale.withValues(alpha: 0.5)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 3
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round;

    final path = Path()
      ..moveTo(size.width * 0.04, size.height * 0.86)
      ..lineTo(size.width * 0.28, size.height * 0.62)
      ..lineTo(size.width * 0.44, size.height * 0.74)
      ..lineTo(size.width * 0.68, size.height * 0.34)
      ..lineTo(size.width * 0.96, size.height * 0.18);

    canvas.drawPath(path, route);
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}

class _Avatar extends StatelessWidget {
  final SocialProfile profile;

  const _Avatar({required this.profile});

  @override
  Widget build(BuildContext context) {
    final label = profile.displayName.isNotEmpty
        ? profile.displayName.trim()
        : profile.username.trim();
    final initials = label.isEmpty
        ? '?'
        : label
            .split(RegExp(r'\s+'))
            .where((part) => part.isNotEmpty)
            .take(2)
            .map((part) => part[0].toUpperCase())
            .join();

    return Container(
      width: AppSpacing.buttonHeightMd,
      height: AppSpacing.buttonHeightMd,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        gradient: LinearGradient(
          colors: [
            AppColors.coralNeon.withValues(alpha: 0.75),
            AppColors.lavandaPale.withValues(alpha: 0.75),
          ],
        ),
      ),
      alignment: Alignment.center,
      child: Text(
        initials,
        style: AppTypography.labelMedium.copyWith(
          color: AppColors.white,
          fontWeight: FontWeight.w700,
        ),
      ),
    );
  }
}
