// UC-STU-deactivate-student: the edit form. The student and the deactivate call are stubbed, so these tests check what
// the form does with each answer and what it sends; the backend's own tests cover who may deactivate and what follows.
// Run against the dev stack (backend + `npm run dev`) with `npm run cy:open`.

const student = {
  id: 4,
  username: 'j.smith@abc.edu',
  firstName: 'John',
  lastName: 'Smith',
  email: 'j.smith@abc.edu',
  enabled: true,
  roles: 'student',
  sectionId: 2,
  sectionName: '2023-2024',
  teamId: 1,
  teamName: 'Team1'
}

const envelope = (data) => ({ flag: true, code: 200, message: 'Success', data })
const found = (overrides = {}) => ({ body: envelope({ ...student, ...overrides }) })
const deactivated = (noticeSent) => ({
  body: envelope({ student: { ...student, enabled: false }, noticeSent })
})

describe('Deactivate a student', () => {
  beforeEach(() => {
    cy.login('b.gates@abc.edu', '123456')
    cy.intercept('POST', '**/students/search*', {
      body: envelope({ content: [student], totalElements: 1 })
    })
  })

  function openEditForm(studentBody) {
    cy.intercept('GET', '**/students/4', studentBody).as('student')
    cy.visit('/students')
    cy.contains('.el-table__row', 'John').find('button').click()
    cy.wait('@student')
  }

  function chooseDeactivate() {
    cy.get('[data-cy=deactivate-student]').click()
  }

  function confirmWarning() {
    cy.get('.el-message-box').contains('button', 'Deactivate').click()
  }

  it('warns with the consequences before sending anything, then sends one POST (steps 3 to 6)', () => {
    cy.intercept('POST', '**/students/4/deactivate', deactivated(true)).as('deactivate')
    openEditForm(found())

    chooseDeactivate()

    cy.get('[data-cy=deactivate-consequences]')
      .should('contain', 'no longer be able to log in')
      .and('contain', 'stays on her team')
      .and('contain', 'may write a peer evaluation')
      .and('contain', 'can be reactivated')
    cy.get('@deactivate.all').should('have.length', 0)

    confirmWarning()

    cy.wait('@deactivate')
    cy.get('.el-message--success').should(
      'contain',
      'John Smith has been deactivated and notified.'
    )
    cy.get('@deactivate.all').should('have.length', 1)
  })

  it('sends nothing when cancelled at the warning, and leaves her active (4a)', () => {
    cy.intercept('POST', '**/students/4/deactivate', deactivated(true)).as('deactivate')
    openEditForm(found())

    chooseDeactivate()
    cy.get('.el-message-box').contains('button', 'Cancel').click()

    cy.get('.el-message-box').should('not.exist')
    cy.get('@deactivate.all').should('have.length', 0)
    cy.get('[data-cy=deactivate-student]').should('exist')
  })

  it('shows the message and reloads her when she is already deactivated (6a)', () => {
    cy.intercept('POST', '**/students/4/deactivate', {
      statusCode: 409,
      body: { flag: false, code: 409, message: 'This student is already deactivated.' }
    }).as('deactivate')
    openEditForm(found())

    chooseDeactivate()
    cy.intercept('GET', '**/students/4', found({ enabled: false })).as('reloaded')
    confirmWarning()

    cy.wait('@deactivate')
    cy.get('.el-message--error').should('contain', 'This student is already deactivated.')
    cy.wait('@reloaded')
    cy.get('[data-cy=deactivate-student]').should('not.exist')
    cy.get('[data-cy=student-status]').should('exist')
  })

  it('says she is deactivated and could not be notified when the mail fails (6b)', () => {
    cy.intercept('POST', '**/students/4/deactivate', deactivated(false)).as('deactivate')
    openEditForm(found())

    chooseDeactivate()
    confirmWarning()

    cy.wait('@deactivate')
    cy.get('.el-message--warning').should(
      'contain',
      'John Smith has been deactivated but could not be notified.'
    )
  })

  it('hides the Status switch for an active student', () => {
    openEditForm(found())

    cy.get('[data-cy=student-status]').should('not.exist')
    cy.get('[data-cy=deactivate-student]').should('exist')
  })

  it('shows the Status switch and no Deactivate button for a deactivated student', () => {
    openEditForm(found({ enabled: false }))

    cy.get('[data-cy=student-status]').should('exist')
    cy.get('[data-cy=deactivate-student]').should('not.exist')
  })

  it('does not ask to enable an active student when saving, so a stale form cannot undo a deactivation', () => {
    cy.intercept('PUT', '**/students/4', found()).as('update')
    openEditForm(found())

    cy.get('.el-dialog').contains('button', 'Confirm').click()

    cy.wait('@update').its('request.body.enabled').should('eq', false)
  })

  it('asks to enable a deactivated student only once the switch is turned on', () => {
    cy.intercept('PUT', '**/students/4', found()).as('update')
    openEditForm(found({ enabled: false }))

    cy.get('[data-cy=student-status]').click()
    cy.get('.el-dialog').contains('button', 'Confirm').click()

    cy.wait('@update').its('request.body.enabled').should('eq', true)
  })
})
